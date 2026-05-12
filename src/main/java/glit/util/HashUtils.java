package glit.util;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Class HashUtils
 */
public class HashUtils{
    /**
     * Hash function for turning content into SHA-1 code
     * @param content - content to be hashed
     * @return hashed content
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
            System.out.println("Długość byte[] hashString z HashUtils: "+hashString.toString().getBytes().length);
            return hashString.toString();

        }catch (NoSuchAlgorithmException e){
            throw new RuntimeException(e + "Brak hasha");
        }

    }

    //zamiana potrzebne przy hashu w drzewie do entries

    /**
     * Function to change hexString to Byte array(used in sha1)
     * @param s - hex String
     * @return byte array from string
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

    public static String byteArrayToHexString(byte [] content){
        return java.util.HexFormat.of().formatHex(content);
    }
}