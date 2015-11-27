package de.cortex42.maerklin.framework.Scripting;

import de.cortex42.maerklin.framework.*;

/**
 * Created by ivo on 18.11.15.
 */
public class ScriptElementWaitForContact extends ScriptElement {
    private final int contactId;
    private final int switchOverTo;
    private final long timeout;
    private final static long DEFAULT_TIMEOUT = 60000L; //60s

    public ScriptElementWaitForContact(int contactId, int switchOverTo) {
        this(contactId, switchOverTo, DEFAULT_TIMEOUT);
    }

    public ScriptElementWaitForContact(int contactId, int switchOverTo, long timeout) {
        this.contactId = contactId;
        this.switchOverTo = switchOverTo;
        this.timeout = timeout;
    }

    @Override
    public void executeElement(final ScriptContext scriptContext) throws FrameworkException {
        final WaitingThreadExchangeObject waitingThreadExchangeObject = new WaitingThreadExchangeObject();

        scriptContext.addPacketListener(
                new PacketListener() {
                    @Override
                    public void packetEvent(PacketEvent packetEvent) {
                        CANPacket canPacket = packetEvent.getCANPacket();

                        if ((canPacket.getCommand() & 0xFE) == CS2CANCommands.S88_EVENT //for response bit
                                && canPacket.getDlc() == CS2CANCommands.S88_EVENT_RESPONSE_DLC
                                && canPacket.getID() == contactId
                                && ((canPacket.getData()[5] & 0xFF) == switchOverTo)) {

                            synchronized (waitingThreadExchangeObject) {
                                waitingThreadExchangeObject.value = true;
                                waitingThreadExchangeObject.notify();
                            }
                            scriptContext.removePacketListener(this);
                        }
                    }
                });

        while (!waitingThreadExchangeObject.value) {
            synchronized (waitingThreadExchangeObject) {
                try {
                    waitingThreadExchangeObject.wait(timeout);
                } catch (InterruptedException e) {
                    throw new FrameworkException(e);
                }
            }
        }
    }
}
