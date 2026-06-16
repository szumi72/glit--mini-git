package glit.util;

import glit.model.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <p>Utility class providing cryptographic hashing and formatting functions for the Glit system.</p>
 *
 * <p>It primarily handles SHA-1 checksum generation for repository objects and offers
 * bidirectional conversions between hexadecimal strings and raw byte arrays to optimize storage
 * and lookups.</p>
 */
public class HashUtils {

    /**
     * <p>Computes the SHA-1 hash of the given byte array and formats it as a 40-character hexadecimal string.</p>
     *
     * <p>This function serves as the core content-addressable identifier generator for all
     * database objects (Blobs, Trees, Commits) within the Glit ecosystem.</p>
     *
     * @param content the raw byte array content to be hashed
     * @return a 40-character hexadecimal {@link String} representing the unique SHA-1 checksum
     * @throws RuntimeException if the SHA-1 hashing algorithm is not supported by the environment
     */
    public static String sha1(byte[] content){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.reset();
            //hash w bytach
            byte[] mes = md.digest(content);
            //zamiana na 40 znakowy String
            StringBuilder hashString = new StringBuilder();
            for(byte b:mes){
                hashString.append(String.format("%02x",b & 0xff));
            }
            return hashString.toString();

        }catch (NoSuchAlgorithmException e){
            throw new RuntimeException(e + "Brak hasha");
        }

    }

    /**
     * <p>Converts a 40-character hexadecimal string representation back into its compact
     * 20-byte binary format array.</p>
     *
     * <p>This conversion is typically used when formatting and serializing Tree object entries,
     * where hashes are stored in raw binary bytes rather than plain text strings to match
     * Git's internal layout.</p>
     *
     * @param s the hexadecimal string to decode
     * @return a byte array containing the decoded binary representation
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * <p>Converts a raw byte array into its equivalent lowercase hexadecimal string representation.</p>
     *
     * <p>Utilizes the modern {@link java.util.HexFormat} API to provide optimized,
     * platform-independent hex formatting.</p>
     *
     * @param content the byte array to encode
     * @return a hexadecimal string representation of the provided bytes
     */
    public static String byteArrayToHexString(byte [] content){
        return java.util.HexFormat.of().formatHex(content);
    }
}