package de.cortex42.maerklin.framework;

/**
 * Created by ivo on 04.12.15.
 */
public class ConfigDataStreamPacketListener implements PacketListener {
    private int compressedFileLength = -1;
    private int decompressedFileLength = -1;
    private int crc = -1;
    private boolean configDataRequestResponseReceived = false;
    private boolean firstDataPacketReceived = false;
    private byte[] decompressedBytes;

    @Override
    public void packetEvent(final PacketEvent packetEvent) {
        CANPacket canPacket = packetEvent.getCANPacket();
        byte[] dataBytes = canPacket.getData();

        if (canPacket.getCommand() == CS2CANCommands.GET_CONFIG_DATA_STREAM) {
            switch (canPacket.getDlc()) {

                case CS2CANCommands.GET_CONFIG_DATA_STREAM_REQUEST_RESPONSE_DLC:
                    configDataRequestResponseReceived = true;

                    byte[] compressedFileLengthBytes = new byte[]{dataBytes[0], dataBytes[1], dataBytes[2], dataBytes[3]};
                    compressedFileLength = ((compressedFileLengthBytes[0] & 0xFF) << 24) | ((compressedFileLengthBytes[1] & 0xFF) << 16)
                            | ((compressedFileLengthBytes[2] & 0xFF) << 8) | ((compressedFileLengthBytes[3] & 0xFF) /*<<0*/);

                    byte[] crcBytes = new byte[]{dataBytes[4], dataBytes[5]};
                    crc = (((crcBytes[0] & 0xFF) << 8) | (crcBytes[1] & 0xFF));

                    break;

                case CS2CANCommands.GET_CONFIG_DATA_STREAM_PACKET_DLC:
                    if (configDataRequestResponseReceived) {
                        int bytesReceived = 0;
                        byte[] buffer = new byte[compressedFileLength + 4]; //allocate 4 bytes more as they contain the decompressed file length

                        for (int j = 0; bytesReceived < compressedFileLength + 4 && j < dataBytes.length; bytesReceived++, j++) {
                            buffer[bytesReceived] = dataBytes[j];
                        }

                        if (!firstDataPacketReceived) {
                            firstDataPacketReceived = true;

                            byte[] decompressedFileLengthBytes = new byte[]{buffer[0], buffer[1], buffer[2], buffer[3]};
                            decompressedFileLength = ((decompressedFileLengthBytes[0] & 0xFF) << 24) | ((decompressedFileLengthBytes[1] & 0xFF) << 16)
                                    | ((decompressedFileLengthBytes[2] & 0xFF) << 8) | ((decompressedFileLengthBytes[3] & 0xFF) /*<<0*/);
                        }

                        if (bytesReceived == compressedFileLength + 4) {
                            byte[] tempBuffer = new byte[compressedFileLength];
                            System.arraycopy(buffer, 4, tempBuffer, 0, compressedFileLength); //Skip decompressed file length bytes

                            int calculatedCRC = DecompressConfigData.calcCRC(tempBuffer);

                            if (calculatedCRC != crc) {
                                throw new FrameworkException(String.format("Incorrect crc value %d but expected %d", calculatedCRC, crc));
                            }

                            decompressedBytes = DecompressConfigData.decompressBytes(tempBuffer, decompressedFileLength);
                        }
                    } else {
                        throw new FrameworkException("Config data stream packet received without previous request response packet.");
                    }
                    break;

                default:
                    break;
            }
        }
    }
}