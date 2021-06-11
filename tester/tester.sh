#!/bin/bash

javac -d . -encoding utf8 ../src/main/java/Tester.java
java -Dfile.encoding=utf8 Tester $@
