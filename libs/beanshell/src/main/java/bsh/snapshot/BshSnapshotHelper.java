package bsh.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class BshSnapshotHelper {
    private static final byte[] MAGIC = new byte[]{'B', 'S', 'H', 'S'};
    private static final int HEADER_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BshSnapshotHelper() {
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read < 0)
                throw new IOException("BeanShell snapshot unexpected end");
            offset += read;
        }
        return data;
    }

    public static void writeEncrypted(BshSnapshot snapshot, OutputStream out, SecretKey key) throws IOException {
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);

        out.write(MAGIC);
        out.write(HEADER_VERSION);
        out.write(iv.length);
        out.write(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            try (ObjectOutputStream objectOut = new ObjectOutputStream(new CipherOutputStream(out, cipher))) {
                objectOut.writeObject(snapshot);
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("BeanShell snapshot encrypt failed", e);
        }
    }

    public static BshSnapshot readEncrypted(InputStream in, SecretKey key) throws IOException {
        byte[] magic = readExact(in, MAGIC.length);
        if (!Arrays.equals(magic, MAGIC))
            throw new IOException("BeanShell snapshot invalid header");

        int version = in.read();
        if (version != HEADER_VERSION)
            throw new IOException("BeanShell snapshot unsupported version: " + version);

        int ivLength = in.read();
        if (ivLength <= 0 || ivLength > 32)
            throw new IOException("BeanShell snapshot invalid IV length");

        byte[] iv = readExact(in, ivLength);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            try (ObjectInputStream objectIn = new FilteringObjectInputStream(new CipherInputStream(in, cipher))) {
                Object object = objectIn.readObject();
                if (!(object instanceof BshSnapshot snapshot))
                    throw new InvalidClassException("BeanShell snapshot unexpected payload");
                if (snapshot.getFormatVersion() != BshSnapshot.FORMAT_VERSION)
                    throw new IOException("BeanShell snapshot unsupported AST format: " + snapshot.getFormatVersion());
                return snapshot;
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("BeanShell snapshot decrypt failed", e);
        } catch (ClassNotFoundException e) {
            throw new IOException("BeanShell snapshot class not found", e);
        }
    }

    private static final class FilteringObjectInputStream extends ObjectInputStream {
        FilteringObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(java.io.ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            Class<?> type = super.resolveClass(desc);
            if (!isAllowed(type))
                throw new InvalidClassException("BeanShell snapshot rejected class: " + type.getName());
            return type;
        }
    }

    private static boolean isAllowed(Class<?> type) {
        if (type.isArray()) {
            Class<?> component = type.getComponentType();
            while (component != null && component.isArray())
                component = component.getComponentType();
            return component != null && (component.isPrimitive() || isAllowed(component));
        }
        if (type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || type == String.class
                || type == Boolean.class
                || type == Character.class
                || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)
                || type == Enum.class || Enum.class.isAssignableFrom(type)
                || type.getName().startsWith("java.lang.invoke.")
                || type.getName().startsWith("java.lang.constant."))
            return true;
        return type.getName().startsWith("bsh.");
    }
}
