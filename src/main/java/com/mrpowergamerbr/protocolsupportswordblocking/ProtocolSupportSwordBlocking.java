package com.mrpowergamerbr.protocolsupportswordblocking;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import protocolsupport.api.ProtocolSupportAPI;
import protocolsupport.api.ProtocolVersion;

import java.lang.reflect.InvocationTargetException;


public class ProtocolSupportSwordBlocking extends JavaPlugin {
    private ProtocolManager protocolManager;
    private SwordBlockingPacketAdapter packetAdapter;

    @Override
    public void onEnable() {
        /* Register packet listener */
        protocolManager = ProtocolLibrary.getProtocolManager();
        packetAdapter = new SwordBlockingPacketAdapter(this);
        protocolManager.addPacketListener(packetAdapter);
    }

    @Override
    public void onDisable() {
        if(protocolManager == null || packetAdapter == null) return;
        protocolManager.removePacketListener(packetAdapter);
    }

    /* Packet adapter code */
    public static class SwordBlockingPacketAdapter extends PacketAdapter {
        private final ProtocolSupportSwordBlocking plugin;

        public SwordBlockingPacketAdapter(ProtocolSupportSwordBlocking plugin) {
            super(plugin,
                    /* Priority */
                    ListenerPriority.HIGHEST,
                    /* Packet types to process */
                    PacketType.Play.Client.BLOCK_PLACE,
                    PacketType.Play.Server.ENTITY_METADATA);

            this.plugin = plugin;
        }

        @Override
        public void onPacketReceiving(PacketEvent event) {
            /* This event handler handles clients before MC 1.9 */
            if(ProtocolSupportAPI.getProtocolVersion(event.getPlayer()).isAfter(ProtocolVersion.MINECRAFT_1_8))
                return;

            /* This event handler handles block place packets */
            if(event.getPacketType() != PacketType.Play.Client.BLOCK_PLACE)
                return;

            /* Cancel packet */
            event.setCancelled(true);

            /* Shallow clone the packet */
            PacketContainer clonedPacket = event.getPacket().shallowClone();

            /* Set held item location to off hand */
            clonedPacket.getHands().write(0, EnumWrappers.Hand.OFF_HAND);

            /* Send packets */
            try {
                /* First resend original packet, and then simulate receive packet */
                plugin.protocolManager.recieveClientPacket(event.getPlayer(), event.getPacket(), false);
                plugin.protocolManager.recieveClientPacket(event.getPlayer(), clonedPacket, false);
            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onPacketSending(PacketEvent event) {
            /* This event handler handles clients before MC 1.9 */
            if(ProtocolSupportAPI.getProtocolVersion(event.getPlayer()).isAfter(ProtocolVersion.MINECRAFT_1_8))
                return;

            /* This event handler handles entity metadata packets, see http://wiki.vg/Entities#Entity_Metadata_Format */
            if(event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA)
                return;

            /* Check if target entity is living entity (see http://wiki.vg/Entities#Living) */
            Entity entity = event.getPacket().getEntityModifier(event.getPlayer().getWorld()).read(0);
            if(!(entity instanceof LivingEntity))
                return;

            /* Clone packet for modification */
            PacketContainer clonedPacket = event.getPacket().deepClone();

            /* Iterate over watchable objects */
            clonedPacket.getWatchableCollectionModifier().read(0).forEach(wwo -> {
                /* Index 6 = hand states */
                if(wwo.getIndex() == 6) {
                    /*
                     * 1 = Main hand, 3 = Off hand
                     * If value is 3 (off hand) , set it back to 1 so plugin can fake sword blocking animation for
                     * older Minecraft clients
                     */
                    Object value = wwo.getValue();
                    if(value instanceof Byte && (byte) value == 3) {
                        wwo.setValue((byte) 1);
                    }
                }
            });

            /* Send modified packet instead of original one */
            event.setPacket(clonedPacket);
        }
    }
}
