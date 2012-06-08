/*************************************************************************
  * tranSMART - translational medicine data mart
 * 
 * Copyright 2008-2012 Janssen Research & Development, LLC.
 * 
 * This product includes software developed at Janssen Research & Development, LLC.
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License 
 * as published by the Free Software  * Foundation, either version 3 of the License, or (at your option) any later version, along with the following terms:
 * 1.	You may convey a work based on this program in accordance with section 5, provided that you retain the above notices.
 * 2.	You may convey verbatim copies of this program code as you receive it, in any medium, provided that you retain the above notices.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS    * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *
 ******************************************************************/
/**
* $Id: Finder.java 11853 2012-01-24 16:45:19Z jliu $
**/
package com.recomdata.search;

import java.io.IOException;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;

/**
 *@author $Author: jliu $
 *@version $Revision: 11853 $
 **/
public class Finder {

	private String[] searchFields = { "contents", "path", "title", "repository", "extension" };
	
	private File index = null;
	
	public Finder(String index) {
		
		this.index = new File(index);
		
	}
	
	public void find(String searchTerms) {

		try {
		    IndexReader reader = IndexReader.open(index);
		    Searcher searcher = new IndexSearcher(reader);
		    Analyzer analyzer = new StandardAnalyzer();
		    QueryParser parser = new MultiFieldQueryParser(searchFields, analyzer);
		    Query query = parser.parse(searchTerms.toLowerCase());
			TopDocCollector collector = new TopDocCollector(20);
		    searcher.search(query, collector);
		    ScoreDoc[] hits = collector.topDocs().scoreDocs;
			System.out.println("count (20 max) = " + hits.length + "\n");
			for (int i = 0; i < hits.length; i++) {
				query.rewrite(reader);
				display(searcher.doc(hits[i].doc), hits[i].doc, hits[i].score, query, analyzer);
			}		
		} catch (Exception e) {
			System.out.println("exception: " + e.getMessage());
		}

	}

	private void display(Document doc, int id, float score, Query query, Analyzer analyzer) {
		
		System.out.println("repository = " + doc.get("repository"));
		System.out.println("path       = " + doc.get("path"));
		System.out.println("extension  = " + doc.get("extension"));
		System.out.println("title      = " + doc.get("title"));

	    Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("<b>", "</b>"), new QueryScorer(query, "contents"));
	    highlighter.setTextFragmenter(new SimpleFragmenter(50));
		String summary = doc.get("contents");
		TokenStream tokenStream = analyzer.tokenStream("contents", new StringReader(summary));
		try {
			System.out.println("contents   = " + highlighter.getBestFragments(tokenStream, summary, 5, "..."));
		} catch (IOException e) {
			System.out.println("exception: " + e.getMessage());
		}
		
		System.out.println();
	}

	public int searchCount(LinkedHashMap<String, ArrayList<String> > searchTerms, LinkedHashMap<String, ArrayList<String> > filterTerms) {

		Query query = buildQuery(searchTerms);
		Filter filter = buildFilter(filterTerms);

		try {
		    IndexReader reader = IndexReader.open(index);
		    Searcher searcher = new IndexSearcher(reader);
		    TopDocCollector collector = new TopDocCollector(1000);
		    if (filter != null) {
		    	searcher.search(query, filter, collector);
		    } else {
		    	searcher.search(query, collector);
		    }
		    ScoreDoc[] hits = collector.topDocs().scoreDocs;
			return hits.length;
		} catch (Exception e) {
			System.out.println("exception: " + e.getMessage());
			return 0;
		}

	}

	public void search(LinkedHashMap<String, ArrayList<String> > searchTerms, LinkedHashMap<String, ArrayList<String> > filterTerms, int max, int offset) {

		Query query = buildQuery(searchTerms);
		Filter filter = buildFilter(filterTerms);

		try {
		    IndexReader reader = IndexReader.open(index);
		    Searcher searcher = new IndexSearcher(reader);
		    Analyzer analyzer = new StandardAnalyzer();
		    TopDocCollector collector = new TopDocCollector(offset + max);
		    if (filter != null) {
			    System.out.println("query = " + query.toString());
			    System.out.println("filter = " + filter.toString());
		    	searcher.search(query, filter, collector);
		    } else {
			    System.out.println("query = " + query.toString());
		    	searcher.search(query, collector);
		    }
		    ScoreDoc[] hits = collector.topDocs().scoreDocs;
		    int size = hits.length - offset < max ? hits.length - offset : max;
			System.out.println("search count=" + hits.length + ", size=" + size +", max=" + max + ", offset=" + offset);
			for (int i = offset; i < offset + max && i < hits.length; i++) {
				query.rewrite(reader);
				display(searcher.doc(hits[i].doc), hits[i].doc, hits[i].score, query, analyzer);
			}
		} catch (Exception e) {
			System.out.println("exception: " + e.getMessage());
		}

	}

	private Query buildQuery(LinkedHashMap<String, ArrayList<String> > searchTerms) {

		BooleanQuery andQuery = new BooleanQuery();

		for (String key : searchTerms.keySet()) {
			ArrayList<String> list = searchTerms.get(key);
			ArrayList<Query> queries = new ArrayList<Query>();
			for (String value : list) {
				if (value.indexOf(" ") == -1) {
					Term term = new Term("contents", value.toLowerCase());
					TermQuery termQuery = new TermQuery(term);
					queries.add(termQuery);
				} else {
					String[] values = value.split(" ");
					PhraseQuery phraseQuery = new PhraseQuery();
					for (String v : values) {
						Term term = new Term("contents", v.toLowerCase());
						phraseQuery.add(term);
					}
					queries.add(phraseQuery);
				}
			}
			addQueries(andQuery, queries);

		}
		
		return andQuery;

	}

	private Filter buildFilter(LinkedHashMap<String, ArrayList<String> > filterTerms) {

		BooleanQuery andQuery = new BooleanQuery();
		if (filterTerms.containsKey("REPOSITORY")) {
			ArrayList<String> list = filterTerms.get("REPOSITORY");
			ArrayList<Query> queries = new ArrayList<Query>();
			for (String value : list) {
					Term term = new Term("repository", value);
					TermQuery termQuery = new TermQuery(term);
					queries.add(termQuery);
			}
			addQueries(andQuery, queries);
		}

		if (filterTerms.containsKey("PATH")) {
			try {
				ArrayList<String> list = filterTerms.get("PATH");
				if (list.size() > 0) {
					StringReader reader = new StringReader(list.get(0));
					StandardAnalyzer analyzer = new StandardAnalyzer();
					TokenStream tokenizer = analyzer.tokenStream("path", reader);
					PhraseQuery phraseQuery = new PhraseQuery();
					Token token = new Token();
					for (token = tokenizer.next(token); token != null; token = tokenizer.next(token)) {
						Term term = new Term("path", token.term());
						phraseQuery.add(term);
					}
					andQuery.add(phraseQuery, BooleanClause.Occur.MUST);
				}
			} catch (IOException ex) {
				// do nothing
			}
		}
		
		if (filterTerms.containsKey("EXTENSION")) {
			ArrayList<String> list = filterTerms.get("EXTENSION");
			ArrayList<Query> queries = new ArrayList<Query>();
			for (String value : list) {
				Term term = new Term("extension", value.toLowerCase());
				TermQuery termQuery = new TermQuery(term);
				queries.add(termQuery);
			}
			addQueries(andQuery, queries);
		}

		if (filterTerms.containsKey("NOTEXTENSION")) {
			ArrayList<String> list = filterTerms.get("NOTEXTENSION");
			for (String value : list) {
				Term term = new Term("extension", value.toLowerCase());
				TermQuery termQuery = new TermQuery(term);
				andQuery.add(termQuery, BooleanClause.Occur.MUST_NOT);
			}
		}

		if (andQuery.clauses().size() > 0) {
			return new QueryWrapperFilter(andQuery);
		}
		return null;

	}
	
	private void addQueries(BooleanQuery andQuery, ArrayList<Query> queries) {
		
		if (queries.size() == 1) {
			andQuery.add(queries.get(0), BooleanClause.Occur.MUST);
		} else if (queries.size() > 1) {
			BooleanQuery orQuery = new BooleanQuery();
			for (Query query : queries) {
				orQuery.add(query, BooleanClause.Occur.SHOULD);
			}
			andQuery.add(orQuery, BooleanClause.Occur.MUST);
		}
		
	}
		
	private String escapeSpecialChars(String s) {
		
		s = s.replace("\\", "\\\\");
		s = s.replace("+", "\\+");
		s = s.replace("-", "\\-");
		s = s.replace("&&", "\\&\\&");
		s = s.replace("||", "\\|\\|");
		s = s.replace("!", "\\!");
		s = s.replace("(", "\\(");
		s = s.replace(")", "\\)");
		s = s.replace("{", "\\{");
		s = s.replace("}", "\\}");
		s = s.replace("[", "\\[");
		s = s.replace("]", "\\]");
		s = s.replace("^", "\\^");
		s = s.replace("\"", "\\\"");
		s = s.replace("~", "\\~");
		s = s.replace("*", "\\*");
		s = s.replace("?", "\\?");
		s = s.replace(":", "\\:");
		if (s.indexOf(" ") != -1) {
			s = "\"" + s + "\"";
		}
		return s;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		String usage = "usage: Finder [-index <index path>] <search terms>";
		String indexPath = "index";
		StringBuilder searchTerms = new StringBuilder();

		if (args.length == 0) {
			System.out.println(usage);
			return;
		}
		
		for (int i = 0; i < args.length; i++) {
			if (args[i].toLowerCase().equals("-index")) {
				if (i >= args.length - 1) {
					System.out.println(usage);
					return;
				}
				i++;
				indexPath = args[i];
			} else if (i != args.length - 1) {
				System.out.println(usage);
				return;
			} else {
				if (searchTerms.length() > 0) {
					searchTerms.append(" ");
				}
				searchTerms.append(args[i]);
			}
		}
	
		Finder finder = new Finder(indexPath);
		
		try {
			if (searchTerms.length() > 0) {
				finder.find(searchTerms.toString());
			} else {
				LinkedHashMap<String, ArrayList<String> > search = new LinkedHashMap<String, ArrayList<String> >();
				LinkedHashMap<String, ArrayList<String> > filter = new LinkedHashMap<String, ArrayList<String> >();
				ArrayList<String> genes = new ArrayList<String>();
				genes.add("met");
				search.put("GENE", genes);
				System.out.println("count = " + finder.searchCount(search, filter));
				finder.search(search, filter, 20, 0);
			}
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
		
	}
	
}