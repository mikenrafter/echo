package eu.mrogalski.saidit.features.audioprocessing.services;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

/**
 * Utility class for audio encoding with multiple quality levels.
 * 
 * This class provides functionality for encoding audio at different bitrates.
 * The AAC encoding implementation is based on the working reference from
 * the fix/auto-save-and-performance branch.
 */
public class AudioEncoder {
    
    /**
     * Encoding quality presets
     */
    public enum Quality {
        LOW(64000),     // 64 kbps - suitable for speech
        MEDIUM(128000), // 128 kbps - balanced quality/size
        HIGH(256000);   // 256 kbps - high quality
        
        private final int bitrate;
        
        Quality(int bitrate) {
            this.bitrate = bitrate;
        }
        
        public int getBitrate() {
            return bitrate;
        }
    }
    
    /**
     * Supported audio codecs
     */
    public enum Codec {
        AAC(MediaFormat.MIMETYPE_AUDIO_AAC);  // Advanced Audio Coding - good compatibility
        
        private final String mimeType;
        
        Codec(String mimeType) {
            this.mimeType = mimeType;
        }
        
        public String getMimeType() {
            return mimeType;
        }
    }
    
    /**
     * Checks if a specific codec is available on this device.
     * 
     * @param codec Codec to check
     * @return true if supported, false otherwise
     */
    public static boolean isCodecSupported(Codec codec) {
        try {
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
            for (MediaCodecInfo codecInfo : codecInfos) {
                if (!codecInfo.isEncoder()) {
                    continue;
                }
                String[] types = codecInfo.getSupportedTypes();
                for (String type : types) {
                    if (type.equalsIgnoreCase(codec.getMimeType())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
