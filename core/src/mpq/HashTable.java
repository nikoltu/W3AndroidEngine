package mpq;

import java.util.function.IntPredicate;

import mpq.data.HashTableEntry;

public class HashTable {
	public static final int BLOCK_EMPTY_ALWAYS = 0xFFFFFFFF;
	public static final int BLOCK_EMPTY_NOW = 0xFFFFFFFE;
	private Entry[] bucketArray;
	
	// raw constructor, assumes every entry is not null and the array is a power of 2
	public HashTable(Entry[] entries){
		bucketArray = entries;
	}
	
	public int lookupBlock(HashLookup what) throws MPQException{
		return lookupBlock(what, Integer.MAX_VALUE, 0, block -> true);
	}
	
	/**
	 * Looks a name up the way Warcraft III's Storm does: every entry of the probe chain with the name's hash is
	 * considered, not the first. Entries of deleted files and entries pointing past the block table are skipped, as are
	 * those whose block the archive does not take as a file; protectors fill the table with such decoys ahead of the
	 * real entry. Among the usable ones, the entry of the wanted locale is chosen, else the neutral one, else the first.
	 */
	public int lookupBlock(HashLookup what, int blockCount, int preferredLocale, IntPredicate usableBlock) throws MPQException{
		int mask = bucketArray.length-1;
		int index = what.index & mask;
		int neutral = -1;
		int first = -1;
		for(int pos = index ; ; ){
			Entry temp = bucketArray[pos];
			if(temp.blockIndex == BLOCK_EMPTY_ALWAYS) break;
			if(temp.getHash() == what.hash && temp.blockIndex != BLOCK_EMPTY_NOW
					&& Integer.compareUnsigned(temp.blockIndex, blockCount) < 0 && usableBlock.test(temp.blockIndex)){
				int locale = temp.locale & 0xFFFF;
				if(locale == preferredLocale) return temp.blockIndex;
				if(locale == 0 && neutral < 0) neutral = temp.blockIndex;
				if(first < 0) first = temp.blockIndex;
			}
			pos = ( pos + 1 ) & mask;
			if(pos == index) break;
		}
		if(neutral >= 0) return neutral;
		if(first >= 0) return first;
		throw MPQException.LOOKUP_NOT_FOUND;
	}
	
	/*public static int lookupBlock(Entry[] hashtable, byte[] file) throws FileNotFoundException{
		int mask = hashtable.length-1;
		int index = Cryption.HashString(file, Cryption.MPQ_HASH_TABLE_OFFSET) & mask;
		long hash = Cryption.HashString(file, Cryption.MPQ_HASH_NAME_A) & 0xFFFFFFFFL | (long)Cryption.HashString(file, Cryption.MPQ_HASH_NAME_B)<<32;
		for(int pos = index ; ; ){
			Entry temp = hashtable[pos];
			if(temp.getBlockIndex() == BLOCK_EMPTY_ALWAYS) break;
			if(temp.getHash() == hash) return temp.getBlockIndex();
			pos = ( pos + 1 ) & mask;
			if(pos == index) break;
		}
		throw new FileNotFoundException("hash not in hashtable");
	}*/
	
	/*public static int lookupBlock(Entry[] hashtable, String file) throws FileNotFoundException{
		return  lookupBlock(hashtable, Cryption.stringToHashable(file));
	}*/
		
	// entry is an internal data type and as such performs no safety checks
	public static class Entry{
		public long hash;
		public short locale;
		public short platform;
		public int blockIndex;
		
		// raw constructor
		public Entry(){
		}
		
		public Entry(HashTableEntry source){
			hash = source.getHash();
			locale = source.getLocale();
			platform = source.getPlatform();
			blockIndex = source.getBlockIndex();
		}

		public long getHash() {
			return hash;
		}

		public short getLocale() {
			return locale;
		}

		public short getPlatform() {
			return platform;
		}

		public int getBlockIndex() {
			return blockIndex;
		}

		
	}
}
