rem delete indexes\Master\*.*
copy indexes\Biomarkers\*.* indexes\Master\*.*
java -Xms256m -Xmx1024m -cp indexer.jar com.recomdata.search.Merger -index "\indexer\indexes\Master" "\indexer\indexes\Conferences"
java -Xms256m -Xmx1024m -cp indexer.jar com.recomdata.search.Merger -index "\indexer\indexes\Master" "\indexer\indexes\DIP"
java -Xms256m -Xmx1024m -cp indexer.jar com.recomdata.search.Merger -index "\indexer\indexes\Master" "\indexer\indexes\Jubilant Oncology"
