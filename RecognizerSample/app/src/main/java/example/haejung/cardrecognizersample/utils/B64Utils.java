package example.haejung.cardrecognizersample.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class B64Utils {
    public static String encodeToBase64(Bitmap image, Bitmap.CompressFormat compressFormat, int quality) throws IOException {
        ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
        image.compress(compressFormat, quality, byteArrayOS);
        String b64EncodedImaged = Base64.encodeToString(byteArrayOS.toByteArray(), Base64.DEFAULT);
        byteArrayOS.close();
        return b64EncodedImaged;
    }

    public static Bitmap decodeBase64(String input) {
        byte[] decodedBytes = Base64.decode(input, 0);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }
}
