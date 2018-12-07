import java.io.*;
import java.util.*;

public class HuffMainCompress {
	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	
	
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
	
	
	public static void main(String[] args) {
		
		System.out.println("Huffman Compress Main");
		File inf = FileSelector.selectFile();
		File outf = FileSelector.saveFile();
		if (inf == null || outf == null) {
			System.err.println("input or output file cancelled");
			return;
		}
		BitInputStream bis = new BitInputStream(inf);
		BitOutputStream bos = new BitOutputStream(outf);
		HuffProcessor hp = new HuffProcessor();
		hp.compress(bis, bos);
		System.out.printf("compress from %s to %s\n", 
		                   inf.getName(),outf.getName());
		System.out.printf("file: %d bits to %d bits\n",inf.length()*8,outf.length()*8);
		System.out.printf("read %d bits, wrote %d bits\n", 
				           bis.bitsRead(),bos.bitsWritten());
		long diff = bis.bitsRead() - bos.bitsWritten();
		System.out.printf("bits saved = %d\n",diff);
	}
}