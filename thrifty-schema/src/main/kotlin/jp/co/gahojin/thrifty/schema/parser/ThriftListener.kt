/*
 * Thrifty
 *
 * Copyright (c) Microsoft Corporation
 * Copyright (c) GAHOJIN, Inc.
 *
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * THIS CODE IS PROVIDED ON AN  *AS IS* BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING
 * WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE,
 * FITNESS FOR A PARTICULAR PURPOSE, MERCHANTABLITY OR NON-INFRINGEMENT.
 *
 * See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */
package jp.co.gahojin.thrifty.schema.parser

import jp.co.gahojin.thrifty.schema.ErrorReporter
import jp.co.gahojin.thrifty.schema.Location
import jp.co.gahojin.thrifty.schema.NamespaceScope
import jp.co.gahojin.thrifty.schema.Requiredness
import jp.co.gahojin.thrifty.schema.antlr.AntlrThriftBaseListener
import jp.co.gahojin.thrifty.schema.antlr.AntlrThriftLexer
import jp.co.gahojin.thrifty.schema.antlr.AntlrThriftParser
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode
import java.util.*

/**
 * A set of callbacks that, when used with a [org.antlr.v4.runtime.tree.ParseTreeWalker],
 * assemble a [ThriftFileElement] from an [AntlrThriftParser].
 *
 * Instances of this class are single-use; after walking a parse tree, it will contain
 * that parser's state as thrifty-schema parser elements.
 *
 * @param tokenStream the same token stream used with the corresponding [AntlrThriftParser];
 * this stream will be queried for "hidden" tokens containing parsed doc
 * comments.
 * @param errorReporter an error reporting mechanism, used to communicate errors during parsing.
 * @param location a location pointing at the beginning of the file being parsed.
 */
internal class ThriftListener(
    private val tokenStream: CommonTokenStream,
    private val errorReporter: ErrorReporter,
    private val location: Location,
) : AntlrThriftBaseListener() {

    // We need to record which comment tokens have been treated as trailing documentation,
    // so that scanning for leading doc tokens for subsequent elements knows where to stop.
    // We can do this with a bitset tracking token indices of trailing-comment tokens.
    private val trailingDocTokenIndexes = BitSet(INITIAL_BITSET_CAPACITY)

    private val includes = mutableListOf<IncludeElement>()
    private val namespaces = mutableListOf<NamespaceElement>()
    private val enums = mutableListOf<EnumElement>()
    private val typedefs = mutableListOf<TypedefElement>()
    private val structs = mutableListOf<StructElement>()
    private val unions = mutableListOf<StructElement>()
    private val exceptions = mutableListOf<StructElement>()
    private val consts = mutableListOf<ConstElement>()
    private val services = mutableListOf<ServiceElement>()

    fun buildFileElement(): ThriftFileElement {
        // Default JVM subtypes to the JVM namespace if present
        namespaces.find { it.scope == NamespaceScope.JVM }?.let { jvmElement ->
            NamespaceScope.jvmMembers().subtract(namespaces.map { it.scope }.toSet()).forEach { subType ->
                namespaces.add(jvmElement.copy(scope = subType))
            }
        }
        return ThriftFileElement(
            location = location,
            includes = includes,
            namespaces = namespaces,
            typedefs = typedefs,
            enums = enums,
            structs = structs,
            unions = unions,
            exceptions = exceptions,
            constants = consts,
            services = services,
        )
    }

    override fun exitInclude(ctx: AntlrThriftParser.IncludeContext) {
        val pathNode = ctx.LITERAL()
        val path = unquote(locationOf(pathNode), pathNode.text, false)

        includes.add(IncludeElement(locationOf(ctx), false, path))
    }

    override fun exitCppInclude(ctx: AntlrThriftParser.CppIncludeContext) {
        val pathNode = ctx.LITERAL()
        val path = unquote(locationOf(pathNode), pathNode.text, false)

        includes.add(IncludeElement(locationOf(ctx), true, path))
    }

    override fun exitStandardNamespace(ctx: AntlrThriftParser.StandardNamespaceContext) {
        val scopeName = ctx.namespaceScope().text
        val name = ctx.ns.text

        val annotations = annotationsFromAntlr(ctx.annotationList())

        val scope = NamespaceScope.forThriftName(scopeName) ?: run {
            errorReporter.warn(locationOf(ctx.namespaceScope()), "Unknown namespace scope '$scopeName'")
            return
        }

        val element = NamespaceElement(
            location = locationOf(ctx),
            scope = scope,
            namespace = name,
            annotations = annotations,
        )

        namespaces.add(element)
    }

    override fun exitPhpNamespace(ctx: AntlrThriftParser.PhpNamespaceContext) {
        val element = NamespaceElement(
            location = locationOf(ctx),
            scope = NamespaceScope.PHP,
            namespace = unquote(locationOf(ctx.LITERAL()), ctx.LITERAL().text),
            annotations = annotationsFromAntlr(ctx.annotationList()),
        )

        namespaces.add(element)
    }

    override fun exitXsdNamespace(ctx: AntlrThriftParser.XsdNamespaceContext) {
        errorReporter.error(locationOf(ctx), "'xsd_namespace' is unsupported")
    }

    override fun exitSenum(ctx: AntlrThriftParser.SenumContext) {
        errorReporter.error(locationOf(ctx), "'senum' is unsupported; use 'enum' instead")
    }

    override fun exitEnumDef(ctx: AntlrThriftParser.EnumDefContext) {
        val enumName = ctx.IDENTIFIER().text

        var nextValue = 0
        val values = mutableSetOf<Int>()

        val members = ArrayList<EnumMemberElement>(ctx.enumMember().size)
        for (memberContext in ctx.enumMember()) {
            var value = nextValue

            memberContext.INTEGER()?.also {
                value = parseInt(it)
            }

            if (!values.add(value)) {
                errorReporter.error(locationOf(memberContext), "duplicate enum value: $value")
                continue
            }

            nextValue = value + 1

            val element = EnumMemberElement(
                location = locationOf(memberContext),
                name = memberContext.IDENTIFIER().text,
                value = value,
                documentation = formatJavadoc(memberContext),
                annotations = annotationsFromAntlr(memberContext.annotationList()),
            )

            members.add(element)
        }

        val doc = formatJavadoc(ctx)
        val element = EnumElement(
            location = locationOf(ctx),
            name = enumName,
            documentation = doc,
            annotations = annotationsFromAntlr(ctx.annotationList()),
            members = members,
        )

        enums.add(element)
    }

    override fun exitStructDef(ctx: AntlrThriftParser.StructDefContext) {
        val name = ctx.IDENTIFIER().text
        val fields = parseFieldList(ctx.field())

        val element = StructElement(
            location = locationOf(ctx),
            name = name,
            fields = fields,
            type = StructElement.Type.STRUCT,
            documentation = formatJavadoc(ctx),
            annotations = annotationsFromAntlr(ctx.annotationList()),
        )

        structs.add(element)
    }

    override fun exitUnionDef(ctx: AntlrThriftParser.UnionDefContext) {
        val name = ctx.IDENTIFIER().text
        val fields = parseFieldList(ctx.field())

        for (i in fields.indices) {
            val element = fields[i]
            if (element.requiredness == Requiredness.REQUIRED) {
                val fieldContext = ctx.field(i)
                errorReporter.error(locationOf(fieldContext), "unions cannot have required fields")
            }
        }

        val element = StructElement(
            location = locationOf(ctx),
            name = name,
            fields = fields,
            type = StructElement.Type.UNION,
            documentation = formatJavadoc(ctx),
            annotations = annotationsFromAntlr(ctx.annotationList()),
        )

        unions.add(element)
    }

    override fun exitExceptionDef(ctx: AntlrThriftParser.ExceptionDefContext) {
        val name = ctx.IDENTIFIER().text
        val fields = parseFieldList(ctx.field())

        val element = StructElement(
            location = locationOf(ctx),
            name = name,
            fields = fields,
            type = StructElement.Type.EXCEPTION,
            documentation = formatJavadoc(ctx),
            annotations = annotationsFromAntlr(ctx.annotationList()),
        )

        exceptions.add(element)
    }

    private fun parseFieldList(
        contexts: List<AntlrThriftParser.FieldContext>,
        defaultRequiredness: Requiredness = Requiredness.DEFAULT,
    ): List<FieldElement> {
        val fields = mutableListOf<FieldElement>()
        val ids = mutableSetOf<Int>()

        var nextValue = 1
        for (fieldContext in contexts) {
            val element = parseField(nextValue, fieldContext, defaultRequiredness)
            if (element != null) {
                fields += element

                if (!ids.add(element.fieldId)) {
                    errorReporter.error(locationOf(fieldContext), "duplicate field ID: ${element.fieldId}")
                }

                if (element.fieldId <= 0) {
                    errorReporter.error(locationOf(fieldContext), "field ID must be greater than zero")
                }

                if (element.fieldId >= nextValue) {
                    nextValue = element.fieldId + 1
                }
            } else {
                // assert-fail here?
                ++nextValue // this represents an error condition
            }
        }

        return fields
    }

    private fun parseField(
        defaultValue: Int,
        ctx: AntlrThriftParser.FieldContext,
        defaultRequiredness: Requiredness
    ): FieldElement? {
        val fieldId = ctx.INTEGER()?.let { parseInt(it) } ?: defaultValue
        val fieldName = ctx.IDENTIFIER().text

        val requiredness = ctx.requiredness()?.let {
            when (it.text) {
                "required" -> Requiredness.REQUIRED
                "optional" -> Requiredness.OPTIONAL
                else -> throw AssertionError("Unexpected requiredness value: ${it.text}")
            }
        } ?: defaultRequiredness

        return FieldElement(
            location = locationOf(ctx),
            documentation = formatJavadoc(ctx),
            fieldId = fieldId,
            requiredness = requiredness,
            type = typeElementOf(ctx.fieldType()),
            name = fieldName,
            annotations = annotationsFromAntlr(ctx.annotationList()),
            constValue = constValueElementOf(ctx.constValue()),
        )
    }

    override fun exitTypedef(ctx: AntlrThriftParser.TypedefContext) {
        val oldType = typeElementOf(ctx.fieldType())

        val typedef = TypedefElement(
            location = locationOf(ctx),
            documentation = formatJavadoc(ctx),
            annotations = annotationsFromAntlr(ctx.annotationList()),
            oldType = oldType,
            newName = ctx.IDENTIFIER().text,
        )

        typedefs.add(typedef)
    }

    override fun exitConstDef(ctx: AntlrThriftParser.ConstDefContext) {
        val constValue = constValueElementOf(ctx.constValue()) ?: run {
            errorReporter.error(locationOf(ctx.constValue()), "Invalid const value")
            return
        }

        val element = ConstElement(
            location = locationOf(ctx),
            documentation = formatJavadoc(ctx),
            type = typeElementOf(ctx.fieldType()),
            name = ctx.IDENTIFIER().text,
            value = constValue,
        )

        consts.add(element)
    }

    override fun exitServiceDef(ctx: AntlrThriftParser.ServiceDefContext) {
        val name = ctx.name.text

        val extendsService = ctx.superType?.let {
            val superType = typeElementOf(it)

            if (superType !is ScalarTypeElement) {
                errorReporter.error(locationOf(ctx), "services cannot extend collections")
                return
            }

            superType
        }

        val service = ServiceElement(
            location = locationOf(ctx),
            name = name,
            functions = parseFunctionList(ctx.function()),
            extendsService = extendsService,
            documentation = formatJavadoc(ctx),
            annotations = annotationsFromAntlr(ctx.annotationList()),
        )

        services.add(service)
    }

    private fun parseFunctionList(functionContexts: List<AntlrThriftParser.FunctionContext>): List<FunctionElement> {
        val functions = mutableListOf<FunctionElement>()

        for (ctx in functionContexts) {
            val name = ctx.IDENTIFIER().text

            val returnType = ctx.fieldType()?.let {
                typeElementOf(it)
            } ?: run {
                val token = ctx.getToken(AntlrThriftLexer.VOID, 0)
                    ?: throw AssertionError("Function has no return type, and no VOID token - grammar error")
                val loc = locationOf(token)

                // Do people actually annotate 'void'?  We'll find out!
                ScalarTypeElement(loc, "void", null)
            }

            val isOneway = ctx.ONEWAY() != null

            val maybeThrowsList = ctx.throwsList()?.let {
                parseFieldList(it.fieldList().field())
            }

            val function = FunctionElement(
                location = locationOf(ctx),
                oneWay = isOneway,
                returnType = returnType,
                name = name,
                documentation = formatJavadoc(ctx),
                annotations = annotationsFromAntlr(ctx.annotationList()),
                params = parseFieldList(ctx.fieldList().field(), Requiredness.REQUIRED),
                exceptions = maybeThrowsList ?: emptyList(),
            )

            functions += function
        }

        return functions
    }

    // region Utilities

    private fun annotationsFromAntlr(ctx: AntlrThriftParser.AnnotationListContext?): AnnotationElement? {
        if (ctx == null) {
            return null
        }

        val annotations = mutableMapOf<String, String>()
        for (annotationContext in ctx.annotation()) {
            val name = annotationContext.IDENTIFIER().text
            annotations[name] = annotationContext.LITERAL()?.let {
                unquote(locationOf(it), it.text)
            } ?: "true"
        }

        return AnnotationElement(locationOf(ctx), annotations)
    }

    private fun locationOf(ctx: ParserRuleContext): Location {
        return locationOf(ctx.getStart())
    }

    private fun locationOf(node: TerminalNode): Location {
        return locationOf(node.symbol)
    }

    private fun locationOf(token: Token): Location {
        val line = token.line
        val col = token.charPositionInLine + 1 // Location.col is 1-based, Token.col is 0-based
        return location.at(line, col)
    }

    private fun unquote(
        location: Location,
        literal: String,
        processEscapes: Boolean = true,
    ): String {
        val chars = literal.toCharArray()
        val startChar = chars[0]
        val endChar = chars[chars.size - 1]

        if (startChar != endChar || startChar != '\'' && startChar != '"') {
            throw AssertionError("Incorrect UNESCAPED_LITERAL rule: $literal")
        }

        return buildString(literal.length - 2) {
            var i = 1
            val end = chars.size - 1
            while (i < end) {
                val c = chars[i++]

                if (processEscapes && c == '\\') {
                    if (i == end) {
                        errorReporter.error(location, "Unterminated literal")
                        break
                    }

                    when (val escape = chars[i++]) {
                        'a' -> append(0x7.toChar())
                        'b' -> append('\b')
                        'f' -> append('\u000C')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        'v' -> append(0xB.toChar())
                        '\\' -> append('\\')
                        'u' -> throw UnsupportedOperationException("unicode escapes not yet implemented")
                        else -> if (escape == startChar) {
                            append(startChar)
                        } else {
                            errorReporter.error(location, "invalid escape character: $escape")
                        }
                    }
                } else {
                    append(c)
                }
            }
        }
    }

    private fun typeElementOf(context: AntlrThriftParser.FieldTypeContext): TypeElement {
        context.baseType()?.also {
            if (it.text == "slist") {
                errorReporter.error(locationOf(context), "slist is unsupported; use list<string> instead")
            }

            return ScalarTypeElement(
                location = locationOf(context),
                name = it.text,
                annotations = annotationsFromAntlr(context.annotationList()),
            )
        }

        context.IDENTIFIER()?.also {
            return ScalarTypeElement(
                location = locationOf(context),
                name = it.text,
                annotations = annotationsFromAntlr(context.annotationList()),
            )
        }

        context.containerType()?.also {
            it.mapType()?.also {
                return MapTypeElement(
                    location = locationOf(it),
                    keyType = typeElementOf(it.key),
                    valueType = typeElementOf(it.value),
                    annotations = annotationsFromAntlr(context.annotationList()),
                )
            }

            it.setType()?.also {
                return SetTypeElement(
                    location = locationOf(it),
                    elementType = typeElementOf(it.fieldType()),
                    annotations = annotationsFromAntlr(context.annotationList()),
                )
            }

            it.listType()?.also {
                return ListTypeElement(
                    location = locationOf(it),
                    elementType = typeElementOf(it.fieldType()),
                    annotations = annotationsFromAntlr(context.annotationList()),
                )
            }

            throw AssertionError("Unexpected container type - grammar error!")
        }

        throw AssertionError("Unexpected type - grammar error!")
    }

    private fun constValueElementOf(ctx: AntlrThriftParser.ConstValueContext?): ConstValueElement? {
        if (ctx == null) {
            return null
        }

        ctx.INTEGER()?.also {
            val text = it.text
            try {
                val value = parseLong(it)

                return IntValueElement(locationOf(ctx), text, value)
            } catch (_: NumberFormatException) {
                throw AssertionError("Invalid integer accepted by ANTLR grammar: $text")
            }
        }

        ctx.DOUBLE()?.also {
            val text = it.text
            try {
                val value = java.lang.Double.parseDouble(text)
                return DoubleValueElement(locationOf(ctx), text, value)
            } catch (_: NumberFormatException) {
                throw AssertionError("Invalid double accepted by ANTLR grammar: $text")
            }

        }

        ctx.LITERAL()?.also {
            val text = unquote(locationOf(it), it.text)
            return LiteralValueElement(locationOf(ctx), it.text, text)
        }

        ctx.IDENTIFIER()?.also {
            val id = it.text
            return IdentifierValueElement(locationOf(ctx), id, id)
        }

        ctx.constList()?.also {
             return ListValueElement(
                location = locationOf(ctx),
                thriftText = it.text,
                value = it.constValue().map { constValueElementOf(it) }.requireNoNulls(),
            )
        }

        ctx.constMap()?.also {
            val values = it.constMapEntry().associate {
                val key = checkNotNull(constValueElementOf(it.key))
                val value = checkNotNull(constValueElementOf(it.value))
                key to value
            }
            return MapValueElement(locationOf(ctx), it.text, values)
        }

        throw AssertionError("unreachable")
    }

    private fun formatJavadoc(context: ParserRuleContext): String {
        return formatJavadoc(getLeadingComments(context.getStart()) + getTrailingComments(context.getStop()))
    }

    private fun getLeadingComments(token: Token): List<Token> {
        val hiddenTokens = tokenStream.getHiddenTokensToLeft(token.tokenIndex, Lexer.HIDDEN)

        return hiddenTokens?.filter {
            it.isComment && !trailingDocTokenIndexes.get(it.tokenIndex)
        } ?: emptyList()
    }

    /**
     * Read comments following the given token, until the first newline is encountered.
     *
     * INVARIANT:
     * Assumes that the parse tree is being walked top-down, left to right!
     *
     * Trailing-doc tokens are marked as such, so that subsequent searches for "leading"
     * doc don't grab tokens already used as "trailing" doc.  If the walk order is *not*
     * top-down, left-to-right, then the assumption underpinning the separation of leading
     * and trailing comments is broken.
     *
     * @param endToken the token from which to search for trailing comment tokens.
     * @return a list, possibly empty, of all trailing comment tokens.
     */
    private fun getTrailingComments(endToken: Token): List<Token> {
        val hiddenTokens = tokenStream.getHiddenTokensToRight(endToken.tokenIndex, Lexer.HIDDEN)

        if (hiddenTokens?.isNotEmpty() == true) {
            val maybeTrailingDoc = hiddenTokens.first() // only one trailing comment is possible

            if (maybeTrailingDoc.isComment) {
                trailingDocTokenIndexes.set(maybeTrailingDoc.tokenIndex)
                return listOf<Token>(maybeTrailingDoc)
            }
        }

        return emptyList()
    }

    companion object {
        // A number of tokens that should comfortably accommodate most input files
        // without wildly re-allocating.  Estimated based on the ClientTestThrift
        // and TestThrift files, which contain around ~1200 tokens each.
        private const val INITIAL_BITSET_CAPACITY = 2048
    }

    // endregion
}

private val Token.isComment: Boolean
    get() {
        return when (this.type) {
            AntlrThriftLexer.SLASH_SLASH_COMMENT,
            AntlrThriftLexer.HASH_COMMENT,
            AntlrThriftLexer.MULTILINE_COMMENT -> true

            else -> false
        }
    }

private fun formatJavadoc(commentTokens: List<Token>): String {
    val sb = StringBuilder()

    for (token in commentTokens) {
        val text = token.text
        when (token.type) {
            AntlrThriftLexer.SLASH_SLASH_COMMENT -> formatSingleLineComment(sb, text, "//")

            AntlrThriftLexer.HASH_COMMENT -> formatSingleLineComment(sb, text, "#")

            AntlrThriftLexer.MULTILINE_COMMENT -> formatMultilineComment(sb, text)

            else -> {
                // wat
                val symbolicName = AntlrThriftParser.VOCABULARY.getSymbolicName(token.type)
                val literalName = AntlrThriftParser.VOCABULARY.getLiteralName(token.type)
                val tokenNumber = "${token.type}"
                error("Unexpected comment-token type: ${symbolicName ?: literalName ?: tokenNumber}")
            }
        }
    }

    return sb.toString().trim { it <= ' ' }.let { doc ->
        if (doc.isNotEmpty()) {
            "${doc}\n"
        } else {
            doc
        }
    }
}

private fun formatSingleLineComment(sb: StringBuilder, text: String, prefix: String) {
    var start = prefix.length
    var end = text.length

    while (start < end && Character.isWhitespace(text[start])) {
        ++start
    }

    while (end > start && Character.isWhitespace(text[end - 1])) {
        --end
    }

    if (start != end) {
        sb.append(text.substring(start, end))
    }

    sb.append("\n")
}

private fun formatMultilineComment(sb: StringBuilder, text: String) {
    val chars = text.toCharArray()
    var pos = "/*".length
    val length = chars.size
    var isStartOfLine = true

    while (pos + 1 < length) {
        val c = chars[pos]
        if (c == '*' && chars[pos + 1] == '/') {
            sb.append("\n")
            return
        }

        if (c == '\n') {
            sb.append(c)
            isStartOfLine = true
        } else if (!isStartOfLine) {
            sb.append(c)
        } else if (c == '*') {
            // skip a single subsequent space, if it exists
            if (chars[pos + 1] == ' ') {
                pos += 1
            }

            isStartOfLine = false
        } else if (!Character.isWhitespace(c)) {
            sb.append(c)
            isStartOfLine = false
        }
        ++pos
    }
}

private fun parseInt(node: TerminalNode): Int {
    return parseInt(node.symbol)
}

private fun parseInt(token: Token): Int {
    var text = token.text

    var radix = 10
    if (text.startsWith("0x") || text.startsWith("0X")) {
        radix = 16
        text = text.substring(2)
    }

    return Integer.parseInt(text, radix)
}

private fun parseLong(node: TerminalNode): Long = parseLong(node.symbol)

private fun parseLong(token: Token): Long {
    val text: String
    val radix: Int

    if (token.text.startsWith("0x", ignoreCase = true)) {
        text = token.text.substring(2)
        radix = 16
    } else {
        text = token.text
        radix = 10
    }

    return java.lang.Long.parseLong(text, radix)
}
