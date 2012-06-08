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
/*
 * $Id: Indexer.java 11853 2012-01-24 16:45:19Z jliu $
*/

package com.recomdata.search;

import org.apache.lucene.analysis.standard.*;
import org.apache.lucene.index.*;
import org.apache.lucene.document.*;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;

import java.io.*;
import java.util.*;


public class Indexer {

	private final static String title = "Indexer " + "$Revision: 11853 $";
	private final static int MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB
	private final static int MAX_FIELD_LENGTH = 1000000;
	private final static int CREATE = 0;
	private final static int UPDATE = 1;
	private final static int REPLACE = 2;
	private final static int RESUME = 4;
	private File indexPath;
	private String repositoryName;
	private File documentPath;
	private File resumeLastFile;
	private int operation = -1;
	private boolean skiplast = false;
	private HashSet<String> excludedFiles = new HashSet<String>();
	private static IndexWriter indexWriter;
	private static BufferedWriter checkpointWriter;
	private static int fileCount = 0;
	private static int addedCount = 0;
	private static int updatedCount = 0;
	private static int errorCount = 0;
	private static Term term;
	
	public Indexer(final File indexPath, String repositoryName, final File documentPath, int operation, boolean skiplast) {
		
		this.indexPath = indexPath;
		this.repositoryName = repositoryName;
		this.documentPath = documentPath;
		this.operation = operation;
		if (operation == UPDATE) {
			term = new Term("path");
		}
		this.skiplast = skiplast;
		
	}
		
	public void index() throws IOException {
		File excludedFilesFile = new File("exclude.txt");
		if (excludedFilesFile.exists()) {
			try {
				BufferedReader excludedFilesReader = new BufferedReader(new FileReader(excludedFilesFile));
				String line;
				while ((line = excludedFilesReader.readLine()) != null) {
					excludedFiles.add(line.toLowerCase().replace("\\", "/"));
				}
				excludedFilesReader.close();
			} catch (IOException ex) {
				// do nothing
			}
		}
		File checkpointFile = new File(indexPath, "checkpoint.log");
		if (operation == RESUME) {
			File lockFile = new File(indexPath, "write.lock");
			if (lockFile.exists()) {
				lockFile.delete();
			}
			if (!checkpointFile.exists()) {
				throw new IOException("Unable to resume indexing. The checkpoint file, " + checkpointFile.getAbsolutePath() + ", does not exist.");
			}
			try {
				BufferedReader checkpointReader = new BufferedReader(new FileReader(checkpointFile));
				String lastFile = "";
				String line;
				while ((line = checkpointReader.readLine()) != null) {
					lastFile = line;
				}
				checkpointReader.close();
				if (lastFile != null) {
					resumeLastFile = new File(lastFile);
				}
			} catch (IOException ex) {
				throw new IOException("Unable to resume indexing. Error processing checkpoint file, " + checkpointFile.getAbsolutePath() + " - " + ex.getMessage());
			}
		}

		indexWriter = new IndexWriter(indexPath, new StandardAnalyzer(), operation == CREATE,
				new IndexWriter.MaxFieldLength(MAX_FIELD_LENGTH));
		checkpointWriter = new BufferedWriter(new FileWriter(checkpointFile, operation == UPDATE || operation == RESUME));		
			
		if (operation == REPLACE) {
			Term term = new Term("repository", repositoryName);
			indexWriter.deleteDocuments(term);
		}
		
		index(documentPath);

		checkpointWriter.close();
		
		System.out.println("Optimizing index...");
		indexWriter.optimize();
		indexWriter.close();

		
	}
		
	private void index(final File file) {
		
		if (file.isDirectory()) {
			String[] files = file.list();
			Arrays.sort(files);
			
			for (int i = 0; i < files.length; i++) {
				index(new File(file, files[i]));
			}
		} else {
			if (resumeLastFile != null) {
				if (resumeLastFile.equals(file)) {
					System.out.print("Found last checkpointed file, " + resumeLastFile.getAbsolutePath() + ".");
					resumeLastFile = null;
					if (skiplast) {
						System.out.println("Resuming indexing with next file.");
						return;
					}
					System.out.println("Resuming indexing.");
				} else {
					return;
				}
			}

			int len = documentPath.getAbsolutePath().length() + 1;
			if (documentPath.getAbsolutePath().endsWith("\\")) {
				len--;
			}
//			if (len > file.getAbsolutePath().length()) {
//				len = file.getAbsolutePath().length();
//			} else if (len < 0) {
//				len = 0;
//			}
			String path = "";
			try {
				path = file.getAbsolutePath().substring(len).replace("\\", "/");
			} catch (Exception ex) {
				System.out.println("len = " + len);
				System.out.println("file.getAbsolutePath().length() = " + file.getAbsolutePath().length());
				System.out.println(ex.getMessage());
				System.exit(1);
			}
			
			if (excludedFiles.contains(file.getName().toLowerCase())) {
				System.out.println("Skipping excluded file " + path);
				return;
			}
			
			Document doc = null;
			FileInputStream stream = null;
			try {
				checkpointWriter.write(file.getAbsolutePath());
				checkpointWriter.newLine();
				checkpointWriter.flush();
				
				String extension = null;
				int pos = path.lastIndexOf(".");
				if (pos != -1 && pos != path.length() - 1) {
					extension = path.substring(path.lastIndexOf(".") + 1).toLowerCase();
				}
				fileCount++;
				doc = new Document();
				
				doc.add(new Field("repository", repositoryName, Field.Store.YES, Field.Index.NOT_ANALYZED));
				doc.add(new Field("path", path, Field.Store.YES, Field.Index.ANALYZED));
				if (extension != null) {
					doc.add(new Field("extension", extension, Field.Store.YES, Field.Index.NOT_ANALYZED));					
				}
			    doc.add(new Field("modified", DateTools.timeToString(file.lastModified(), DateTools.Resolution.MINUTE),
				        Field.Store.YES, Field.Index.NOT_ANALYZED));
				
			    if (file.length() <= MAX_FILE_SIZE) {
				    StringWriter body = new StringWriter();
				    BodyContentHandler handler = new BodyContentHandler(body);
				    AutoDetectParser parser = new AutoDetectParser();
				    Metadata metadata = new Metadata();
				    stream = new FileInputStream(file);
				    parser.parse(stream, handler, metadata);
	
				    doc.add(new Field("contents", body.toString(), Field.Store.YES, Field.Index.ANALYZED));
				    
				    String title = metadata.get(Metadata.TITLE);
				    if (title != null && title.length() > 0) {
				    	doc.add(new Field("title", title, Field.Store.YES, Field.Index.ANALYZED));
				    }
				    
				    // TODO: Add additional Metadata fields to index.
			    }
			    if (operation == UPDATE) {
			    	System.out.println("updating " + path);
					indexWriter.updateDocument(term.createTerm(path), doc);
					updatedCount++;
			    } else {
			    	System.out.println("adding " + path);
					indexWriter.addDocument(doc);			    	
					addedCount++;
			    }
			    indexWriter.commit();
			} catch (IOException ex) {
				System.out.print("exception indexing " + path + ": ");
				System.out.println(ex.getMessage());
				errorCount++;
			} catch (Exception ex) {
				System.out.print("exception indexing " + path + ": ");
				System.out.println(ex.getMessage());
				errorCount++;
			}
			
			try {
				if (stream != null) {
					stream.close();
					stream = null;
				}
			} catch (IOException ex) {
				System.out.print("exception closing " + path + ": ");
				System.out.println(ex.getMessage());				
			}
			 
		}
		
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String usage = "usage: Indexer [-create | -update | -replace | -resume] -index <index path> -repository <repository name> -path <document path> [-skiplast]\n" +
			"-create   Creates new index files deleting old ones, if they exist.\n" +
			"-update   Updates existing documents or adds new ones, if they do not exist.\n" +
			"-replace  Replaces all documents in specified repository.\n" +
			"-resume   Resumes indexing at last file from in previous run (or file after last, if using -skiplast option).";
		int operation = -1;
		File indexPath = null;
		String repositoryName = "";
		File documentPath = null;
		boolean skiplast = false;

		System.out.println(title.replace("$", ""));
		if (args.length == 0) {
			System.out.println(usage);
			return;
		}
		
		for (int i = 0; i < args.length; i++) {
			if (args[i].toLowerCase().equals("-create") && operation == -1) {
				operation = CREATE;
			} else if (args[i].toLowerCase().equals("-update") && operation == -1) {
				operation = UPDATE;
			} else if (args[i].toLowerCase().equals("-replace") && operation == -1) {
				operation = REPLACE;
			} else if (args[i].toLowerCase().equals("-resume") && operation == -1) {
				operation = RESUME;
			} else if (args[i].toLowerCase().equals("-index")) {
				if (i >= args.length - 1) {
					System.out.println(usage);
					return;
				}
				i++;
				indexPath = new File(args[i]);
			} else if (args[i].toLowerCase().equals("-repository")) {
				if (i >= args.length - 1) {
					System.out.println(usage);
					return;
				}
				i++;
				repositoryName = args[i];
			} else if (args[i].toLowerCase().equals("-path")) {
				if (i >= args.length - 1) {
					System.out.println(usage);
					return;
				}
				i++;
				String path = args[i];
				// Fix windows quirk where an argument of "C:\" is interpreted as C:"
				if (path.endsWith(":\"")) {
					path = path.replace("\"", "\\");
				}
				documentPath = new File(path);
			} else if (args[i].toLowerCase().equals("-skiplast")) {
				skiplast = true;
			} else if (i != args.length - 1) {
				System.out.println(usage);
				return;
			}
		}
		
		if (indexPath == null || repositoryName == "" || documentPath == null || operation == -1) {
			System.out.println(usage);
			return;			
		}
	
		Indexer indexer = new Indexer(indexPath, repositoryName, documentPath, operation, skiplast);
		
		try {
			indexer.index();
		} catch (IOException ex) {
			System.out.println(ex.getMessage());
		}
		
		System.out.println();
		System.out.println("Repository Name: " + repositoryName);
		System.out.println("Document Path:   " + documentPath.getAbsolutePath());
		System.out.println("Total Files:     " + fileCount);
		System.out.println("Total Added:     " + addedCount);
		System.out.println("Total Updated:   " + updatedCount);
		System.out.println("Total Errors:    " + errorCount);
		
	}

}
