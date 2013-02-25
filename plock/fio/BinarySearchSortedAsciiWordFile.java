package plock.fio;

/** 
  * non-printable ascii and space are considered delimiters to printable ascii words,
  * empty strings are ignored, since it is ascii, no Byte Order Mark is checked.
  * considering there is no buffering (except by OS), you don't want to use this with really
  * long words (a hit or near hit may take a while to validate)
  * Use less than 2GB files
  * everything is compared case-insensitive
  * version 0.9
  * Copyright (C) 2013  Chris Plock
  * 
  *     This program is free software: you can redistribute it and/or modify
  *     it under the terms of the GNU General Public License as published by
  *     the Free Software Foundation, either version 3 of the License, or
  *     (at your option) any later version.
  * 
  *     This program is distributed in the hope that it will be useful,
  *     but WITHOUT ANY WARRANTY; without even the implied warranty of
  *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  *     GNU General Public License for more details.
  * 
  *     You should have received a copy of the GNU General Public License
  *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
  */

import java.io.*;

public class BinarySearchSortedAsciiWordFile {
	private final File file;
	private static boolean debug=false;
	
	public BinarySearchSortedAsciiWordFile(File f) {
		this.file = f;
	}
	/** @return null if cannot read the file, else true if word was found */
	public Boolean find(String word) {
		if (!file.exists() || !file.canRead()) {
			return null;
		}
		try {
		    RandomAccessFile raf = new RandomAccessFile(file, "r");
		    Boolean found = booleanSearch(word.toLowerCase().toCharArray(), raf, 0, (int)raf.length());
		    raf.close();
		    return found;
		} catch (IOException e) {
			return null;
		}
	}
	/** end is exclusive */
	public Boolean booleanSearch(final char[] chars, final RandomAccessFile raf, int start, int end) throws IOException {
		// start==end==0 for example is empty since end is exclusive
		// could be negative start is the last letter processed of the last word, which can extend beyond the end
		if (end<=start) {
			return false;
		}

		int mid = (start+end)/2;

		if (debug) {
			if (end-start<80) {
				raf.seek(start);
				byte[] buff = new byte[end-start];
				int midBuff = buff.length/2;
				raf.readFully(buff);
				String before = new String(buff, 0, midBuff, "UTF-8").replaceAll("\n", " ");
				String after = new String(buff, midBuff+1,buff.length-midBuff-1).replaceAll("\n", " ");
				char c = (char)buff[midBuff];
				if (c == '\n') {c=' ';}
				System.out.println("scanning ["+before+'|'+c+'|'+after+']'+raf.readLine());
			}
		}
				
		// consider [0] s=0,e=1,mid=0        [0,1] s=0,e=2,mid=0         [0,1,2] s=0,e=3,mid=1
		// eval the mid value, left is [s,mid), right is [mid+1,end)
		//      eval 0, []  []                  eval 0, []  [1]          eval 1, [0] [2]

		// we generally will hit in the middle of a word,
		//   so ignore the current word as our child left range will have the first letter of the word,
		//   or, we are in the middle of a set of delimiters
		//   and therefore we scan ahead to the beginning of the next word by looking for the last consecutive delimiter
		// the only gotcha is when we are length of 1 and the left will be empty and we are at the start of a word,
		//   so we first check if we have an immediate match and if not continue to gobble of the current word/word fragment,
		//   if we do have a match, we have to look backwards 1 byte to see if we are at the beginning of the file or have a delimiter
		//   just in case our word was a suffix 
		
		raf.seek(mid);

		// check immediately for a match, this may go past end
		int b = raf.read();
		int wordPos = 0; // index into the search word
		int filePos = mid+1; // this will help us track against 'end'
		while (b != -1 && wordPos<chars.length && chars[wordPos] == Character.toLowerCase(b)) {
		  b = raf.read();
		  wordPos++;
		}
		filePos += wordPos;
		if (wordPos == chars.length) { // check if we match up the entire requested word
			// we need to make sure we had the start of a word, check for beginning of file else start-1 for delimiter
			if (start==0) {return true;}
			raf.seek(mid-1);
			b = raf.read();
			if (b==-1) {return null;} // someone truncated underneath us!
			if (b<=' ' || b>'~') {
				return true; // we found delimiter before matching word, woot!
			}
			raf.seek(filePos); // return us to where we were since no match
		}

		// skip rest of the current non-matching word, but don't bother going past end
		while(filePos < end && b>' ' && b<='~') {
			// this will break on first terminal it sees read into 'b'
		    // this also breaks out on EOF or end (EOF shouldn't need to be checked, but just in case)
			b = raf.read();
			filePos++;
		}

		if (b == -1) {return null;} // someone truncated underneath us!
		if (filePos==end) {
		    // we reached end of file or range before next word so simply process left
			return booleanSearch(chars, raf, start, mid);
		}

		// now skip delimiters to get the first character of the word for comparing, don't need to go past end
		while(filePos < end && (b<=' ' || b>'~')) {
			b = raf.read();
			filePos++;
		}
		if (b == -1) {return null;} // someone truncated underneath us!
		if (filePos == end && (b<=' ' || b>'~')) { // we reached end of range before next word so process left
			return booleanSearch(chars, raf, start, mid);
		}

		// compare byte to byte until we hit EOF, a mismatch, or end of word
		wordPos = 0;
		while (b>=0 && wordPos<chars.length && chars[wordPos] == Character.toLowerCase(b)) {
		  b = raf.read();
		  wordPos++;
		  filePos++;
		}

		if (wordPos != chars.length) {
			if (b==-1) {return null;} // someone truncated underneath us!
			if (filePos == end) {
			    // we hit end before finishing word so word would come after which means it is not on the right, look left
				return booleanSearch(chars, raf, start, mid);
			}
			// we didn't find a match, figure out if we should recurse on left or right
			if (chars[wordPos] < Character.toLowerCase(b)) { // current char is less than what we read, do left
			    if (debug) {System.out.println("<---      "+new String(chars, 0, wordPos)+'['+(char)b+']'+raf.readLine());}
				return booleanSearch(chars, raf, start, mid);
			} else { // well, do right!
				int rightStart = (int)raf.getFilePointer();
			    if (debug) {System.out.println("     ---> "+new String(chars, 0, wordPos)+'['+(char)b+']'+raf.readLine());}
				// we can throw away the current word already, so start here for the right side
				return booleanSearch(chars, raf, rightStart, end);
			}
		}
		if (wordPos == chars.length && (b == -1 || b <= ' ' || b > '~')) {
			// we found EOF or a delimiter as the next character, we have a match!
			return true;
		}
		// next byte was a character instead of a delimiter, so we're close but we need to
		// look prior to this value only
		return booleanSearch(chars, raf, start, mid);
	}
	
	public static void main(String[] args) throws IOException {
	    if (args[0].equals("-v")) {debug=true;}
		if (args.length < (debug?2:1)) {
			System.out.println("Usage: java BinarySearchSortedAsciiWordFile [-v] <pathToFile> <searchWord ...>");
		} else {
			File wordFile = new File(args[debug?1:0]);
			BinarySearchSortedAsciiWordFile search = new BinarySearchSortedAsciiWordFile(wordFile);
			for (int i=(debug?2:1); i<args.length; i++) {
				long start = System.currentTimeMillis();
				String searchWord = args[i];
				Boolean found = search.find(searchWord);
				long end = System.currentTimeMillis();
				System.out.println(found+" - "+searchWord+" in "+(end-start)+"ms");
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			String word = reader.readLine();
			long totalStart = System.currentTimeMillis();
			while (word != null) {
				long start = System.currentTimeMillis();
				Boolean found = search.find(word);
				long end = System.currentTimeMillis();
				//System.out.println(found+" - "+word+" in "+(end-start)+"ms");
				word = reader.readLine();
			}
			System.out.println("total in " + (System.currentTimeMillis()-totalStart)/1000 +"s");
		}
	}
}