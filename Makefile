.EXPORT_ALL_VARIABLES:
.PHONY: test deploy

SHELL = bash

VERSION = $(shell cat VERSION)
DATE = $(shell date)

repl:
	clojure -A:test:nrepl -R:test:nrepl -e "(-main)" -r

clear:
	rm -rf target && clojure -A:build

jar:
	clojure -A:build

build:	jar
	cp target/sdcpop-0.0.1-standalone.jar npm/sdcpop/bin/sdcpop.jar
	cp target/sdcpop-0.0.1-standalone.jar target/sdcpop.jar

test:
	clojure -A:test:runner
