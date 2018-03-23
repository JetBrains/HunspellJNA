package dk.dren.hunspell;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * The simple hunspell library frontend which takes care of creating
 * and singleton'ing the library instance (no need to load it more than once
 * per process) .
 *
 * The Hunspell java bindings are licensed under the same terms as Hunspell itself (GPL/LGPL/MPL tri-license),
 * see the file COPYING.txt in the root of the distribution for the exact terms.
 *
 * @author Flemming Frandsen (flfr at stibo dot com)
 */

public class Hunspell implements HunspellLibrary {

	public native Pointer Hunspell_create(String affpath, String dpath);

	public native void Hunspell_destroy(Pointer pHunspell);

	public native int Hunspell_spell(Pointer pHunspell, byte[] word);

	public native String Hunspell_get_dic_encoding(Pointer pHunspell);

	public native int Hunspell_suggest(Pointer pHunspell, PointerByReference slst, byte[] word);

	public native int Hunspell_analyze(Pointer pHunspell, PointerByReference slst, byte[] word);

	public native int Hunspell_stem(Pointer pHunspell, PointerByReference slst, byte[] word);

	public native void Hunspell_free_list(Pointer pHunspell, PointerByReference slst, int n);

	public native int Hunspell_add(Pointer pHunspell, byte[] word);

	public native int Hunspell_remove(Pointer pHunspell, byte[] word);

    /**
     * The Singleton instance of Hunspell
     */
    private static Hunspell hunspell = null;

	/**
	 * The library file that was loaded.
	 */
	private String libFile;

    /**
     * The instance of the HunspellManager, looks for the native lib in the
     * default directories
     */
    public static Hunspell getInstance() throws UnsatisfiedLinkError, UnsupportedOperationException {
		return getInstance(null);
    }

    /**
     * The instance of the HunspellManager, looks for the native lib in
     * the directory specified.
     *
     * @param libDir Optional absolute directory where the native lib can be found.
     */
    public static Hunspell getInstance(String libDir) throws UnsatisfiedLinkError, UnsupportedOperationException {
        if (hunspell != null) {
            return hunspell;
        }

        hunspell = new Hunspell(libDir);
        return hunspell;
    }



    /**
     * Constructor for the library, loads the native lib.
     *
     * Loading is done in the first of the following three ways that works:
     * 1) Unmodified load in the provided directory.
     * 2) libFile stripped back to the base name (^lib(.*)\.so on unix)
     * 3) The library is searched for in the classpath, extracted to disk and loaded.
     *
     * @param libDir Optional absolute directory where the native lib can be found.
     * @throws UnsupportedOperationException if the OS or architecture is simply not supported.
     */
    protected Hunspell(String libDir) throws UnsatisfiedLinkError, UnsupportedOperationException {
		libFile = libDir != null ? libDir + "/" + libName() : libNameBare();
	}

	static {
		String libFile = libNameBare();
		try {
			Native.register(libFile);
		} catch (UnsatisfiedLinkError urgh) {

			// Oh dear, the library was not found in the file system, let's try the classpath
			libFile = libName();
			InputStream is = Hunspell.class.getResourceAsStream("/"+libFile);
			if (is == null) {
				throw new UnsatisfiedLinkError("Can't find "+libFile+
											   " in the filesystem nor in the classpath\n"+
											   urgh);
			}

			// Extract the library from the classpath into a temp file.
			File lib;
			FileOutputStream fos = null;
			try {
				lib = File.createTempFile("jna", "."+libFile);
				lib.deleteOnExit();
				fos = new FileOutputStream(lib);
				int count;
				byte[] buf = new byte[1024];
				while ((count = is.read(buf, 0, buf.length)) > 0) {
					fos.write(buf, 0, count);
				}

			} catch(IOException e) {
				throw new Error("Failed to create temporary file for "+libFile, e);

			} finally {
				try { is.close(); } catch(IOException e) { }
				if (fos != null) {
					try { fos.close(); } catch(IOException e) { }
				}
			}
			//System.out.println("Loading temp lib: "+lib.getAbsolutePath());
			Native.register(lib.getAbsolutePath());
		}
    }

	public String getLibFile() {
		return libFile;
	}

    /**
     * Calculate the filename of the native hunspell lib.
     * The files have completely different names to allow them to live
     * in the same directory and avoid confusion.
     */
    public static String libName() throws UnsupportedOperationException {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.startsWith("windows")) {
			return libNameBare()+".dll";

		} else if (os.startsWith("mac os x")) {
			//	    return libNameBare()+".dylib";
			return "lib"+libNameBare()+".dylib";

		} else {
			return "lib"+libNameBare()+".so";
		}
    }

	static String libNameBare() throws UnsupportedOperationException {
		String os = System.getProperty("os.name").toLowerCase();
		String arch = System.getProperty("os.arch").toLowerCase();

		// Annoying that Java doesn't have consistent names for the arch types:
		boolean x86  = arch.equals("x86")    || arch.equals("i386")  || arch.equals("i686");
		boolean amd64= arch.equals("x86_64") || arch.equals("amd64") || arch.equals("ia64n");

		if (os.startsWith("windows")) {
			if (x86) {
				return "hunspell-win-x86-32";
			}
			if (amd64) {
				return "hunspell-win-x86-64";
			}

		} else if (os.startsWith("mac os x")) {
			if (x86) {
				return "hunspell-darwin-x86-32";
			}
			if (amd64) {
				return "hunspell-darwin-x86-64";
			}
			if (arch.equals("ppc")) {
				return "hunspell-darwin-ppc-32";
			}

		} else if (os.startsWith("linux")) {
			if (x86) {
				return "hunspell-linux-x86-32";
			}
			if (amd64) {
				return "hunspell-linux-x86-64";
			}

		} else if (os.startsWith("sunos")) {
			//if (arch.equals("sparc")) {
			//	return "hunspell-sunos-sparc-64";
			//}
		}

		throw new UnsupportedOperationException("Unknown OS/arch: "+os+"/"+arch);
    }

    /**
     * This is the cache where we keep the already loaded dictionaries around
     */
    private HashMap<String, Dictionary> map = new HashMap<String, Dictionary>();

    /**
     * This is the where we keep the last modified date for dictionary files
     */
	private HashMap<String, Long> modMap = new HashMap<String, Long>();

    /**
     * Gets an instance of the dictionary.
     * 
     * If a cached instance of the dictionary exists it will be returned even
     * if it's contents aren't in sync with the affix/dictionary files. If you
     * would instead prefer a dictionary that reflects the current file
     * contents you should first remove the stale dictionary from the cache
     * and destroy it.
     *
     * @param baseFileName the base name of the dictionary,
     * passing /dict/da_DK means that the files /dict/da_DK.dic
     * and /dict/da_DK.aff get loaded
     */
    public Dictionary getDictionary(String baseFileName)
		throws FileNotFoundException, UnsupportedEncodingException {
    	
    	return getDictionary(baseFileName, false);
    }
    
    /**
     * Gets an instance of the dictionary optionally checking to see if it has
     * changed since the last time it was cached.
     *
     * @param baseFileName the base name of the dictionary,
     * passing /dict/da_DK means that the files /dict/da_DK.dic
     * and /dict/da_DK.aff get loaded
     * @param isUpdateAllowed
     */
    public Dictionary getDictionary(String baseFileName, boolean isUpdateAllowed)
		throws FileNotFoundException, UnsupportedEncodingException {

    	
    	Dictionary result = map.get(baseFileName);
		
		if (result == null || isUpdateAllowed) {
			// Check the last modified date to detect if the dictionary files have changed and reload if they have.
			File dicFile = new File(baseFileName + ".dic");
			File affFile = new File(baseFileName + ".aff");
	
			Long lastModified;
			
			try {
				lastModified = dicFile.lastModified() + affFile.lastModified();
			} catch (SecurityException e) {
				// Meh, there's nothing we can do about it, but it should never happen anyway.
				lastModified = 0L;
			}
			
			//
			if (result != null && !lastModified.equals(modMap.get(baseFileName))) {
				// NOTE: We're only removing the dictionary from the cache. The memory
				// used by the dictionary isn't being freed up here because other
				// references to the dictionary may still be in use. If not then the
				// finalizer will release the memory
				destroyDictionary(baseFileName);
				result = null;
			}
			
			if (result == null) {
				result = new Dictionary(baseFileName);
				
				map.put(baseFileName, result);
				modMap.put(baseFileName, lastModified);
			}
		}
		
		return result;
    }

    /**
     * Removes a dictionary from the internal cache.
     * 
     * Note: that this will not free up any memory used by the Dictionary
     * itself.
     *
     * @param baseFileName the base name of the dictionary, as passed to
     * getDictionary()
     */
    public void destroyDictionary(String baseFileName) {
		if (map.containsKey(baseFileName)) {
			map.remove(baseFileName);
			modMap.remove(baseFileName);
		}
    }

    /**
     * Class representing a single dictionary.
     */
    public class Dictionary {
    	
		/**
		 * The pointer to the hunspell object as returned by the hunspell
		 * constructor.
		 */
		private Pointer hunspellDict = null;

		/**
		 * The encoding used by this dictionary
		 */
		private String encoding;

		/**
		 * Creates an instance of the dictionary.
		 * @param baseFileName the base name of the dictionary,
		 */
		Dictionary(String baseFileName) throws FileNotFoundException,
											   UnsupportedEncodingException {
			File dic = new File(baseFileName + ".dic");
			File aff = new File(baseFileName + ".aff");

			if (!dic.canRead() || !aff.canRead()) {
				throw new FileNotFoundException("The dictionary files "+
												baseFileName+
												"(.aff|.dic) could not be read");
			}

			hunspellDict = Hunspell_create(aff.toString(), dic.toString());
			encoding = Hunspell_get_dic_encoding(hunspellDict);

			// This will blow up if the encoding doesn't exist
			Native.toByteArray("test", encoding);
		}
		
		@Override
		protected void finalize() throws Throwable {
			// Makes sure that the dictionary was destroyed before it's garbage collected.
			destroy();
			
			super.finalize();
		}

		/**
		 * Deallocate the dictionary.
		 */
		public void destroy() {
			if (hunspellDict != null) {
				Hunspell_destroy(hunspellDict);
				hunspellDict = null;
			}
		}

		/**
		 * Check if a word is spelled correctly
		 *
		 * @param word The word to check.
		 */
		public boolean misspelled(String word) {
			return Hunspell_spell(hunspellDict, Native.toByteArray(word, encoding)) == 0;
		}

		/**
		 * Add a word to dictionary
		 * 
		 * @param word The word to add
		 */
		public boolean add(String word){
			return Hunspell_add(hunspellDict, Native.toByteArray(word, encoding)) == 0;
		}

		/**
		 * Remove a word from dictionary
		 *
		 * @param word The word to remove
		 */
		public boolean remove(String word){
			return Hunspell_remove(hunspellDict, Native.toByteArray(word, encoding)) == 0;
		}

		/**
		 * Returns a list of suggestions
		 *
		 * @param word The word to check and offer suggestions for
		 */
		public List<String> suggest(String word) {
			int suggestionsCount = 0;
			PointerByReference suggestions = new PointerByReference();
			suggestionsCount = Hunspell_suggest(hunspellDict, suggestions, Native.toByteArray(word, encoding));

			return pointerToCStringsToList(suggestions, suggestionsCount);
		}

		/**
		 * Returns a list of analyses
		 *
		 * @param word The word to analyze
		 */
		public List<String> analyze(String word) {
			int analysesCount = 0;
			PointerByReference analyses = new PointerByReference();
			analysesCount = Hunspell_analyze(hunspellDict, analyses, Native.toByteArray(word, encoding));

			return pointerToCStringsToList(analyses, analysesCount);
		}

		/**
		 * Returns a list of stems
		 *
		 * @param word The word to find the stem for
		 */
		public List<String> stem(String word) {
            int stemsCount = 0;
            PointerByReference stems = new PointerByReference();
            stemsCount = Hunspell_stem(hunspellDict, stems, Native.toByteArray(word, encoding));

            return pointerToCStringsToList(stems, stemsCount);
        }

		private List<String> pointerToCStringsToList(PointerByReference slst, int n) {
			if ( n == 0 ) {
				return Collections.emptyList();
			}

			List<String> strings = new ArrayList<String>(n);

			try {
				// Get each of the suggestions out of the pointer array.
				Pointer[] pointerArray = slst.getValue().
					getPointerArray(0, n);

				for (int i=0; i<n; i++) {

					/* This only works for 8 bit chars, luckily hunspell uses either
					   8 bit encodings or utf8, if someone implements support in
					   hunspell for utf16 we are in trouble.
					*/
					long len = pointerArray[i].indexOf(0, (byte)0);
					if (len != -1) {
						if (len > Integer.MAX_VALUE) {
							throw new RuntimeException(
													   "String improperly terminated: " + len);
						}
						byte[] data = pointerArray[i].getByteArray(0, (int)len);
						strings.add(new String(data, encoding));
					}
				}

			} catch (UnsupportedEncodingException e) {
				// Shouldn't happen...
			} finally {
				Hunspell_free_list(hunspellDict, slst, n);
			}

			return strings;
		}
    }
}
