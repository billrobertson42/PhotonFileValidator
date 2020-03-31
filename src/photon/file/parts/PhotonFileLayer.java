/*
 * MIT License
 *
 * Copyright (c) 2018 Bonosoft
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package photon.file.parts;

import photon.file.SlicedFileHeader;
import photon.file.parts.photon.PhotonFileHeader;
import photon.file.ui.PhotonAALevel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.*;

/**
 * by bn on 01/07/2018.
 */
public class PhotonFileLayer {
    private float layerPositionZ;
    private float layerExposure;
    private float layerOffTimeSeconds;
    private int dataAddress;
    private int dataSize;
    private int unknown1;
    private int unknown2;
    private int unknown3;
    private int unknown4;

    private byte[] imageData;

    private byte[] packedLayerImage;

    private ArrayList<BitSet> islandRows;
    private int isLandsCount;
    private long pixels;

    private ArrayList<PhotonFileLayer> antiAliasLayers = new ArrayList<>();

    private boolean extendsMargin;

    public void setFileHeader(SlicedFileHeader fileHeader) {
        this.photonFileHeader = fileHeader;
        antiAliasLayers.forEach(x->x.setFileHeader(fileHeader));
    }

    private SlicedFileHeader photonFileHeader;
    public boolean isCalculated;

    private PhotonFileLayer(PhotonInputStream ds) throws Exception {
        layerPositionZ = ds.readFloat();
        layerExposure = ds.readFloat();
        layerOffTimeSeconds = ds.readFloat();

        dataAddress = ds.readInt();
        dataSize = ds.readInt();

        unknown1 = ds.readInt();
        unknown2 = ds.readInt();
        unknown3 = ds.readInt();
        unknown4 = ds.readInt();
    }

    public PhotonFileLayer() {
        // blank constructor for filling in later.
    }

    public PhotonFileLayer(PhotonFileLayer photonFileLayer, SlicedFileHeader photonFileHeader) {
        layerPositionZ = photonFileLayer.layerPositionZ;
        layerExposure = photonFileLayer.layerExposure;
        layerOffTimeSeconds = photonFileLayer.layerOffTimeSeconds;
        dataAddress = photonFileLayer.dataAddress;
        dataAddress = photonFileLayer.dataSize;

        this.photonFileHeader = photonFileHeader;

        // Dont copy data, we are building new AA layers anyway
        //this.imageData = copy();
        //this.packedLayerImage = copy();
    }

    public int savePos(int dataPosition) throws Exception {
        dataAddress = dataPosition;
        return dataPosition + dataSize;
    }

    public void save(PhotonOutputStream os) throws Exception {
        os.writeFloat(layerPositionZ);
        os.writeFloat(layerExposure);
        os.writeFloat(layerOffTimeSeconds);

        os.writeInt(dataAddress);
        os.writeInt(dataSize);

        os.writeInt(unknown1);
        os.writeInt(unknown2);
        os.writeInt(unknown3);
        os.writeInt(unknown4);
    }

    public void saveData(PhotonOutputStream os) throws Exception {
        os.write(imageData, 0, dataSize);
    }

    public static int getByteSize() {
        return 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4;
    }

    public ArrayList<BitSet> unpackImage(int resolutionX, int resolutionY) {
        pixels = 0;
        resolutionX = resolutionX - 1;
        ArrayList<BitSet> unpackedImage = new ArrayList<>(resolutionY);
        BitSet currentRow = new BitSet(resolutionX);
        unpackedImage.add(currentRow);
        int x = 0;
        for (byte rle : imageData) {
            int length = rle & 0x7F;
            boolean color = (rle & 0x80) == 0x80;
            if (color) {
                pixels += length;
            }
            int endPosition = x + (length - 1);
            int lineEnd = Integer.min(endPosition, resolutionX);
            if (color) {
                currentRow.set(x, 1 + lineEnd);
            }
            if (endPosition > resolutionX) {
                currentRow = new BitSet();
                unpackedImage.add(currentRow);
                lineEnd = endPosition - (resolutionX + 1);
                if (color) {
                    currentRow.set(0, 1 + lineEnd);
                }
            }
            x = lineEnd + 1;
            if (x > resolutionX) {
                currentRow = new BitSet();
                unpackedImage.add(currentRow);
                x = 0;
            }
        }
        return unpackedImage;
    }

    private void aaPixels(ArrayList<BitSet> unpackedImage, PhotonLayer photonLayer) {
        photonLayer.clear();

        for (int y = 0; y < unpackedImage.size(); y++) {
            BitSet currentRow = unpackedImage.get(y);
            if (currentRow != null) {
                for (int x = 0; x < currentRow.length(); x++) {
                    if (currentRow.get(x)) {
                        photonLayer.unSupported(x, y);
                    }
                }
            }
        }
    }

    private void unknownPixels(ArrayList<BitSet> unpackedImage, PhotonLayer photonLayer) {
        photonLayer.clear();

        for (int y = 0; y < unpackedImage.size(); y++) {
            BitSet currentRow = unpackedImage.get(y);
            if (currentRow != null) {
            	int x = 0;
            	while ((x = currentRow.nextSetBit(x)) >= 0) {
            		photonLayer.supported(x, y);
            		++x;
            	}
            }
        }
    }

    private void calculate(ArrayList<BitSet> unpackedImage, ArrayList<BitSet> previousUnpackedImage, PhotonLayer photonLayer) {
        islandRows = new ArrayList<>();
        isLandsCount = 0;

        photonLayer.clear();

        for (int y = 0; y < unpackedImage.size(); y++) {
            BitSet currentRow = unpackedImage.get(y);
            BitSet prevRow = previousUnpackedImage != null ? previousUnpackedImage.get(y) : null;
            if (currentRow != null) {
            	int x = 0;
            	while ((x = currentRow.nextSetBit(x)) >= 0) {
                    if (prevRow == null || prevRow.get(x)) {
                        photonLayer.supported(x, y);
                    } else {
                        photonLayer.island(x, y);
                    }
                    ++x;
            	}
            }
        }

        photonLayer.reduce();

        isLandsCount = photonLayer.setIslands(islandRows);
    }

    /**
     * Read a photon layer from a normal image file (e.g. a png)
     * @param width of the image
     * @param height of the image
     * @param input stream to read the image from
     * @param aaLevel The AA level to read the image at
     * @return a PhotonFileLayer of the image
     * @throws Exception on failure
     */
    public static PhotonFileLayer readLayer(int width, int height, InputStream input, PhotonAALevel aaLevel) throws Exception {
        PhotonFileLayer[] targets = new PhotonFileLayer[aaLevel.levels];
        BufferedImage img = ImageIO.read(input);
        PhotonLayer[] layers = new PhotonLayer[aaLevel.levels];
        int[] thresholds = new int[aaLevel.levels];

        for (int i = 0; i < aaLevel.levels; i++) {
            targets[i] = new PhotonFileLayer();

            layers[i] = new PhotonLayer(width, height);
            layers[i].clear();
            // +1 on each of these as we want everything to have at least _some_ threshold.
            thresholds[i] = (int)((float)(i+1) * 255.0f / (aaLevel.levels+1));
        }

        // TODO:: can we speed this up using .getRaster()? Would need to be pushed inside PhotonLayer I think.
        for( int y=0; y<height; y++) {
            for( int x=0; x<width; x++) {
                // assume the image is greyscale. TODO:: average the values?
                int pixel = img.getRGB(x,y) & 0xff;
                for( int a=0; a<aaLevel.levels; a++ ) {
                    if (pixel >= thresholds[a]) {
                        layers[a].supported(x, y);
                    } else {
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < aaLevel.levels; i++) {
            targets[i].saveLayer(layers[i]);
        }

        for (int i = 1; i < aaLevel.levels; i++) {
            targets[0].addAntiAliasLayer(targets[i]);
        }

        return targets[0];
    }

    public static List<PhotonFileLayer> readLayers(PhotonFileHeader photonFileHeader, byte[] file, int margin, IPhotonProgress iPhotonProgress) throws Exception {
        PhotonLayer photonLayer = new PhotonLayer(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());

        List<PhotonFileLayer> layers = new ArrayList<>();

        int antiAliasLevel = 1;
        if (photonFileHeader.getVersion() > 1) {
            antiAliasLevel = photonFileHeader.getAALevels();
        }

        int layerCount = photonFileHeader.getNumberOfLayers();

        try (PhotonInputStream ds = new PhotonInputStream(new ByteArrayInputStream(file, photonFileHeader.getLayersDefinitionOffsetAddress(), file.length))) {
            Hashtable<Integer, PhotonFileLayer> layerMap = new Hashtable<>();
            for (int i = 0; i < layerCount; i++) {

                iPhotonProgress.showInfo("Reading photon file layer " + (i + 1) + "/" + photonFileHeader.getNumberOfLayers());

                PhotonFileLayer layer = new PhotonFileLayer(ds);
                layer.photonFileHeader = photonFileHeader;
                layer.imageData = Arrays.copyOfRange(file, layer.dataAddress, layer.dataAddress + layer.dataSize);
                layers.add(layer);
                layerMap.put(i, layer);
            }

            if (antiAliasLevel > 1) {
                for (int a = 0; a < (antiAliasLevel - 1); a++) {
                    for (int i = 0; i < layerCount; i++) {
                        iPhotonProgress.showInfo("Reading photon file AA " + (2 + a) + "/" + antiAliasLevel + " layer " + (i + 1) + "/" + photonFileHeader.getNumberOfLayers());

                        PhotonFileLayer layer = new PhotonFileLayer(ds);
                        layer.photonFileHeader = photonFileHeader;
                        layer.imageData = Arrays.copyOfRange(file, layer.dataAddress, layer.dataAddress + layer.dataSize);

                        layerMap.get(i).addAntiAliasLayer(layer);

                    }
                }
            }
        }

        photonLayer.unLink();
        System.gc();

        return layers;
    }

    private void addAntiAliasLayer(PhotonFileLayer layer) {
        antiAliasLayers.add(layer);
    }

    public static void calculateAALayers(SlicedFileHeader photonFileHeader, List<PhotonFileLayer> layers, PhotonAaMatrix photonAaMatrix, IPhotonProgress iPhotonProgress) throws Exception {
        PhotonLayer photonLayer = new PhotonLayer(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());
        int[][] source = new int[photonFileHeader.getResolutionY()][photonFileHeader.getResolutionX()];

        int i = 0;
        for (PhotonFileLayer layer : layers) {
            ArrayList<BitSet> unpackedImage = layer.unpackImage(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());

            iPhotonProgress.showInfo("Calculating AA for photon file layer " + i + "/" + photonFileHeader.getNumberOfLayers());


            for (int y = 0; y < photonFileHeader.getResolutionY(); y++) {
                for (int x = 0; x < photonFileHeader.getResolutionX(); x++) {
                    source[y][x] = 0;
                }
            }

            for (int y = 0; y < unpackedImage.size(); y++) {
                BitSet currentRow = unpackedImage.get(y);
                if (currentRow != null) {
                    for (int x = 0; x < currentRow.length(); x++) {
                        if (currentRow.get(x)) {
                            source[y][x] = 255;
                        }
                    }
                }
            }

            // Calc
            int[][] target = photonAaMatrix.calc(source);

            // TODO:: Ensure this is at least 1 / that we are running on a file which supports AA
            int aaTresholdDiff = 255 / photonFileHeader.getAALevels();
            int aaTreshold = 0;
            for (PhotonFileLayer aaFileLayer : layer.antiAliasLayers) {
                photonLayer.clear();
                aaTreshold += aaTresholdDiff;

                for (int y = 0; y < photonFileHeader.getResolutionY(); y++) {
                    for (int x = 0; x < photonFileHeader.getResolutionX(); x++) {
                        if (target[y][x] >= aaTreshold) {
                            photonLayer.supported(x, y);
                        }
                    }
                }

                aaFileLayer.saveLayer(photonLayer);
            }

            i++;
        }
        photonLayer.unLink();
        System.gc();

    }

    public static void calculateLayers(SlicedFileHeader photonFileHeader, List<PhotonFileLayer> layers, int margin, IPhotonProgress iPhotonProgress) throws Exception {
        PhotonLayer photonLayer = new PhotonLayer(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());
        ArrayList<BitSet> previousUnpackedImage = null;
        int i = 0;
        for (PhotonFileLayer layer : layers) {
            ArrayList<BitSet> unpackedImage = layer.unpackImage(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());

            iPhotonProgress.showInfo("Calculating photon file layer " + i + "/" + photonFileHeader.getNumberOfLayers());

            if (margin > 0) {
                layer.extendsMargin = layer.checkMargin(unpackedImage, margin);
            }

            layer.unknownPixels(unpackedImage, photonLayer);

            layer.calculate(unpackedImage, previousUnpackedImage, photonLayer);

            if (previousUnpackedImage != null) {
                previousUnpackedImage.clear();
            }
            previousUnpackedImage = unpackedImage;

            layer.packedLayerImage = photonLayer.packLayerImage();
            layer.isCalculated = true;

            if (photonFileHeader.hasAA()) {
                for (PhotonFileLayer aaFileLayer : layer.antiAliasLayers) {
                    ArrayList<BitSet> aaUnpackedImage = aaFileLayer.unpackImage(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());
                    PhotonLayer aaPhotonLayer = new PhotonLayer(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());
                    aaFileLayer.unknownPixels(aaUnpackedImage, aaPhotonLayer);
                    aaFileLayer.packedLayerImage = aaPhotonLayer.packLayerImage();
                    aaFileLayer.isCalculated = false;
                }
            }

            i++;
        }
        photonLayer.unLink();
        System.gc();
    }

    public static void calculateLayers(SlicedFileHeader photonFileHeader, List<PhotonFileLayer> layers, int margin, int layerNo) throws Exception {
        PhotonLayer photonLayer = new PhotonLayer(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());
        ArrayList<BitSet> previousUnpackedImage = null;

        if (layerNo > 0) {
            previousUnpackedImage = layers.get(layerNo - 1).unpackImage(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());
        }

        for (int i = 0; i < 2; i++) {
            PhotonFileLayer layer = layers.get(layerNo + i);
            ArrayList<BitSet> unpackedImage = layer.unpackImage(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());

            if (margin > 0) {
                layer.extendsMargin = layer.checkMargin(unpackedImage, margin);
            }

            layer.unknownPixels(unpackedImage, photonLayer);

            layer.calculate(unpackedImage, previousUnpackedImage, photonLayer);

            if (previousUnpackedImage != null) {
                previousUnpackedImage.clear();
            }
            previousUnpackedImage = unpackedImage;

            layer.packedLayerImage = photonLayer.packLayerImage();
            layer.isCalculated = true;

            i++;
        }
        photonLayer.unLink();
        System.gc();
    }

    public ArrayList<PhotonRow> getRows() {
        return PhotonLayer.getRows(packedLayerImage, photonFileHeader.getResolutionX(), isCalculated);
    }

    public ArrayList<BitSet> getIslandRows() {
        return islandRows;
    }

    public int getIsLandsCount() {
        return isLandsCount;
    }

    public long getPixels() {
        return pixels;
    }

    public float getLayerPositionZ() {
        return layerPositionZ;
    }

    public void setLayerPositionZ(float layerPositionZ) {
        this.layerPositionZ = layerPositionZ;
    }

    public float getLayerExposure() {
        return layerExposure;
    }

    public float getLayerOffTime() {
        return layerOffTimeSeconds;
    }

    public void setLayerExposure(float layerExposure) {
        this.layerExposure = layerExposure;
    }

    public void setLayerOffTimeSeconds(float layerOffTimeSeconds) {
        this.layerOffTimeSeconds = layerOffTimeSeconds;
    }

    public void unLink() {
        imageData = null;
        packedLayerImage = null;
        if (islandRows != null) {
            islandRows.clear();
        }
        photonFileHeader = null;
    }

    public boolean doExtendMargin() {
        return extendsMargin;
    }

    private boolean checkMargin(ArrayList<BitSet> unpackedImage, int margin) {
        if (unpackedImage.size() > margin) {
            // check top margin rows
            for (int i = 0; i < margin; i++) {
                if (!unpackedImage.get(i).isEmpty()) {
                    return true;
                }
            }
            // check bottom margin rows
            for (int i = unpackedImage.size() - margin; i < unpackedImage.size(); i++) {
                if (!unpackedImage.get(i).isEmpty()) {
                    return true;
                }
            }

            for (int i = margin; i < unpackedImage.size() - margin; i++) {
                BitSet row = unpackedImage.get(i);
                int nextBit = row.nextSetBit(0);
                if (nextBit >= 0 && nextBit < margin) {
                    return true;
                }
                nextBit = row.nextSetBit(photonFileHeader.getResolutionX() - margin);
                if (nextBit > photonFileHeader.getResolutionX() - margin) {
                    return true;
                }
            }

        }
        return false;
    }


    public BufferedImage getImage() {
        if ( antiAliasLayers.isEmpty()) {
            // No aa, just get current image
            return getLayer().getImage();
        }

        List<PhotonLayer> layers = new ArrayList<>();
        layers.add(getLayer());
        getAntiAlias().stream().forEach(x-> {layers.add(x.getLayer());});


        BufferedImage result = new BufferedImage(photonFileHeader.getResolutionX(),
                photonFileHeader.getResolutionY(),
                BufferedImage.TYPE_INT_RGB);

        int[] colourArray = new int[layers.size()+1];
        for (int i = 0; i <= layers.size() ; i++) {
            float colourDiv = (float)i / layers.size();
            colourArray[i] = new Color(colourDiv, colourDiv, colourDiv).getRGB();
        }

        for (int y = 0; y < photonFileHeader.getResolutionY(); y++) {
            for (int x = 0; x < photonFileHeader.getResolutionX(); x++) {
                int colour = 0;
                for (PhotonLayer l: layers) {
                    // There really has to be a faster way than this.
                    switch(l.get(x,y)) {
                        case PhotonLayer.ISLAND:
                        case PhotonLayer.SUPPORTED:
                        case PhotonLayer.CONNECTED:
                            colour += 1;
                            break;
                    }
                }
                result.setRGB(x, y, colourArray[colour]);
            }
        }

        return result;

    }

    public PhotonLayer getLayer() {
        PhotonLayer photonLayer = new PhotonLayer(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());
        photonLayer.unpackLayerImage(packedLayerImage);
        return photonLayer;
    }

    public void getUpdateLayer(PhotonLayer photonLayer) {
        photonLayer.unpackLayerImage(packedLayerImage);
    }

    public void updateLayerIslands(PhotonLayer photonLayer) {
        islandRows = new ArrayList<>();
        isLandsCount = photonLayer.setIslands(islandRows);
    }

    public void saveLayer(PhotonLayer photonLayer) throws Exception {
        this.packedLayerImage = photonLayer.packLayerImage();
        this.imageData = photonLayer.packImageData();
        this.dataSize = imageData.length;
        islandRows = new ArrayList<>();
        isLandsCount = photonLayer.setIslands(islandRows);
    }

    public ArrayList<BitSet> getUnknownRows() {
        return unpackImage(photonFileHeader.getResolutionX(), photonFileHeader.getResolutionY());
    }

    public PhotonFileLayer getAntiAlias(int a) {
        if (antiAliasLayers.size() > a) {
            return antiAliasLayers.get(a);
        }
        return null;
    }

    public ArrayList<PhotonFileLayer> getAntiAlias() {
        return antiAliasLayers;
    }
}
