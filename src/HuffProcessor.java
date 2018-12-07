import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		int [] counts = readForCounts(in);
		HuffNode root = makeTreeFRomCounts(counts);
		String [] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT,  HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		
		while (true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) {
				break;
			}
			String code = codings[bits];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
		out.close();
		
		
	}
	private void writeHeader(HuffNode root, BitOutputStream out) {
		HuffNode current = root;
		while (current != null) {
			if (current.myLeft != null  && current.myRight != null) {
				out.writeBits(1, 0);
				writeHeader(root.myLeft, out);
				writeHeader(root.myRight, out);
			}
			if (current.myLeft != null && current.myRight == null) {
				out.writeBits(1, 0);
				writeHeader(root.myLeft,out);
			}
			if (current.myLeft == null && current.myRight != null) {
				out.writeBits(1, 0);
				writeHeader(root.myRight,out);
			}
			if (current.myLeft == null && current.myRight == null) {
				out.writeBits(1,1);
				out.writeBits(BITS_PER_WORD +1 , current.myValue);
			}
		}
		out.close();	
	}
	
	private String[] makeCodingsFromTree(HuffNode root) {
		String [] encodings = new String[ALPH_SIZE +1];
		codingHelper(root, "", encodings);
		
		return encodings;
	}
	
	private void codingHelper(HuffNode root, String str, String[] encodings) {
		if (root == null) {
			return;
		}
		if (root.myLeft == null && root.myRight!= null) {
			codingHelper(root.myRight, str+=1, encodings);
		}
		if (root.myLeft != null && root.myRight== null) {
			codingHelper(root.myLeft, str+=0, encodings);
		}
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = str;
			return;
		}
		codingHelper(root.myLeft, str+=0, encodings);
		codingHelper(root.myRight, str+=1, encodings);
		
	}
	
	private HuffNode makeTreeFRomCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int k = 0; counts[k] > 0; k+=1) {
			pq.add(new HuffNode(k, counts[k], null, null));
		}
		
		while (pq.size() >1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0,(left.myWeight+right.myWeight), left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	//returns array of frequencies for each 8 bit stream
	private int[] readForCounts(BitInputStream in) {
		int [] arr = new int [ALPH_SIZE+1];
		while (true) {
			int n = in.readBits(BITS_PER_WORD);
			if (n == -1) {
				break;
			}
			else {
				arr [n] += 1;	
			}
		}
		arr[PSEUDO_EOF] = 1;
		return arr;
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
public void decompress(BitInputStream in, BitOutputStream out) {
		
		int bits = in.readBits(BITS_PER_INT);
		if (bits!= HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	
	
	//reads the stream and saves the values saved in the tree's leafs 
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits == 0) {
					current = current.myLeft;
				}
				else {
					current = current.myRight;
				}
				if (current.myLeft == null && current.myRight==null) {
					if (current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
					
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
				
			}
		}
		out.close();
		
	}


	//reads through the bitstream and finds the leafs, creating a new huffnode with those values
	private HuffNode readTreeHeader(BitInputStream in) {
		int n = in.readBits(1);
		if (n == -1) {
			throw new HuffException("illegal header starts with -1");
		}
		if (n == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode (0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD +1);
			return new HuffNode(value, 0, null, null);
		}
		
	}
}