#!/bin/sh
java -Xms256m -Xmx1024m -cp indexer.jar com.recomdata.search.Indexer -create -index $1 -repository "Biomarker" -path $2
