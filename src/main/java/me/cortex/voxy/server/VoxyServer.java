package me.cortex.voxy.server;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;

public class VoxyServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
    }
}

//The way the initial handshake works
// client joins
// if client supports S2CHello -> send S2CHello (containing mod version info, etc)
// client responds with C2SHello (containing mod version info, etc) -> Server registers all packets relating to voxy for player, sends ACK S2CHello
// on ACK S2CHello, client registers all packet handlers for voxy
// server then sends ConfigSync -> client initalizes the config it will use while on the server



//When a chunk is saved, the ingest service will process it and apply updates,
// this will trigger an update callback which will then trigger updates to the client if they are within range with respect to the expected LoD level stuff
// client will receive update and trigger a render update

//Syncing the LoDs e.g. determining what sections to invalidate in the cache will be very hard
// probably use something like a merkle tree where the client will incrementally determine what sections are invalid with respect to what is on the server
// this will be with respect to the render distance/what sections the client can activly see
// if a section is determined to be invalid, remove the section from the cache and trigger a render update
// this will automatically make the client send a request to get the LoD and majic it works

//If a client requires an LoD but it misses the cache, send a request to the server to fetch it
// spinlock the thread while this happens (ik ik its very bad but there isnt much else to fix it unless everything becomes async (not happening))
// can probably instead of spinlocking the thread, make it work on other tasks that need doing
// so like instead of having 3 different thread pools, have a single thread pool (maybe) and have it poll on each services semaphore with a set timeout

//On a dimension change or when the server tells the client it needs a config change, s2c.ConfigUpdatePacket is sent
// with the config changes (such as changing the dimension hash property)
// the config along with ConditionalConfig will enable servers to have separate configs depending on the world etc
