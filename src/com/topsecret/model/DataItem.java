package com.topsecret.model;

import com.secretlib.model.ChunkData;
import com.secretlib.model.HiDataBag;
import com.secretlib.util.Parameters;

/**
 * @author Florent FRADET
 */
public class DataItem {

    private HiDataBag bag;
    private String name;
    private int length;
    private boolean encrypted;
    private int chunkDataId;


    public DataItem(HiDataBag bag, String name, int length, boolean encrypted, int chunkDataId) {
        this.bag = bag;
        this.name = name;
        this.length = length;
        this.encrypted = encrypted;
        this.chunkDataId = chunkDataId;
    }

    public int getChunkDataId() {
        return chunkDataId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (!encrypted) {
            this.name = name;
            ChunkData dfd = (ChunkData) bag.findById(chunkDataId);
            dfd.setName(name);
            try {
                // No need to encrypt with valid credentials
                dfd.encryptData(new Parameters());
            } catch (Exception e) {
                // NO OP
            }
            length = dfd.getTotalLength();
        }
    }

    public Integer getLength() {
        return length;
    }

    public boolean isEncrypted() {
        return encrypted;
    }
}
