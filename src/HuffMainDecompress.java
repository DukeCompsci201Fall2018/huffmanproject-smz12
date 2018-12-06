import java.io.File;
import java.util.*;
import java.io.*;

public class HuffMainDecompress {
	
	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	
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
	
	
	
	public static void main(String[] args) {
		
		System.out.println("Huffman Decompress Main");
		
		File inf = FileSelector.selectFile();
		File outf = FileSelector.saveFile();
		if (inf == null || outf == null) {
			System.err.println("input or output file cancelled");
			return;
		}
		BitInputStream bis = new BitInputStream(inf);
		BitOutputStream bos = new BitOutputStream(outf);
		HuffProcessor hp = new HuffProcessor();
		hp.decompress(bis, bos);
		System.out.printf("uncompress from %s to %s\n", 
				           inf.getName(),outf.getName());		
		
		System.out.printf("file: %d bits to %d bits\n",inf.length()*8,outf.length()*8);
		System.out.printf("read %d bits, wrote %d bits\n", 
				           bis.bitsRead(),bos.bitsWritten());
		long diff = 8*(outf.length() - inf.length());
		long diff2 = bos.bitsWritten() - bis.bitsRead();
		System.out.printf("%d compared to %d\n",diff,diff2);
	}
}
