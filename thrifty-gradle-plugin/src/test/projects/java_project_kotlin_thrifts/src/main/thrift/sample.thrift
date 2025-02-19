namespace jvm jp.co.gahojin.thrifty.test;

struct Foo {
    1: optional string name;
    2: optional i64 number;
}

service FooFetcher {
    Foo fetchFoo();
}