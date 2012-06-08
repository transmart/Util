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
* $Id: Merger.java 11853 2012-01-24 16:45:19Z jliu $
**/
package com.recomdata.search;

import org.apache.lucene.analysis.standard.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;

import java.io.*;
import java.util.*;

/**
 *@author $Author: jliu $
 *@version $Revision: 11853 $
 **/
public class Merger {

	private final static int MAX_FIELD_LENGTH = 1000000;
	private File sourcePaths[];
	private File indexPath;
	private static IndexWriter writer;
	
	public Merger(final File indexPath, final File sourcePaths[]) {

		this.sourcePaths = sourcePaths;
		this.indexPath = indexPath;

	}
	
	public void merge() throws IOException {
		
		Directory dirs[] = new Directory[sourcePaths.length];
		
		for (int i = 0; i < sourcePaths.length; i++) {
			dirs[i] = FSDirectory.getDirectory(sourcePaths[i]);
		}

		writer = new IndexWriter(indexPath, new StandardAnalyzer(), false,
				new IndexWriter.MaxFieldLength(MAX_FIELD_LENGTH));
		
		System.out.println("Merging indexes...");
		writer.addIndexesNoOptimize(dirs);
		
		System.out.println("Optimizing index...");
		writer.commit();
		writer.optimize();
		writer.close();
		
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		String usage = "usage: Merger -index <index path> <source path 1>...[source path n] \n" +
			"merges source index[es] into index";
		ArrayList<File> sourcePaths = new ArrayList<File>();
		File indexPath = null;

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
				indexPath = new File(args[i]);
			} else if (i != args.length - 1) {
				System.out.println(usage);
				return;
			} else {
				sourcePaths.add(new File(args[i]));
			}
		}
		
		if (indexPath == null || sourcePaths.size() < 1) {
			System.out.println(usage);
			return;			
		}
	
		Merger merger = new Merger(indexPath, (File[]) sourcePaths.toArray(new File[sourcePaths.size()]));
		
		try {
			merger.merge();
		} catch (IOException ex) {
			System.out.println(ex.getMessage());
		}
		
		System.out.println();
		
	}
	
}

