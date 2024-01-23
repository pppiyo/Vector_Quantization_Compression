
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;


public class VectorCompression {
    private static final Random        random       = new Random();
    private final        int           GREY_SCALE   = 256;
    private final        double        DIFFCAP      = 2;
    private final        double        MAX_ITER_NUM = 200;
    private final        int           N;
    private final        int           M;
    private final        BufferedImage oriImg;
    private final BufferedImage compImg;
    private final int           oriWidth;
    private final int           oriHeight;
    private       int[] groupTags;
    private              int[][]       finalCodewords;

    public VectorCompression() {
        oriWidth = 352;
        oriHeight = 288;
        M = 2;
        N = 16;
        oriImg = new BufferedImage(oriWidth, oriHeight, BufferedImage.TYPE_INT_RGB);
        compImg = new BufferedImage(oriWidth, oriHeight, BufferedImage.TYPE_INT_RGB);
    }

    public VectorCompression(int M, int N) {
        oriWidth = 352;
        oriHeight = 288;
        this.N = N;
        this.M = M;
        oriImg = new BufferedImage(oriWidth, oriHeight, BufferedImage.TYPE_INT_RGB);
        compImg = new BufferedImage(oriWidth, oriHeight, BufferedImage.TYPE_INT_RGB);
    }


    public static void main(String[] args) {
        long startTime = System.nanoTime();


        String filename = args[0];
        int M = Integer.parseInt(args[1]);
        int N = Integer.parseInt(args[2]);
        VectorCompression mc = new VectorCompression(M, N);
        mc.readFile(filename);
        BufferedImage procImg = mc.getProcImage(mc.oriImg);
        int[] procPxs = mc.imgToPxs(procImg);
        int[][] vectors = mc.pxsToVectors(procPxs, procImg.getWidth());
        int[][] initCodewords = mc.getInitCodeWord(vectors);
        mc.kmeans(initCodewords, vectors);
        vectors = mc.encodeVectors(vectors);
        int[] finalPxs = mc.vectorsToPxs(vectors, procImg.getWidth());
        mc.pxsToImg(finalPxs, procImg);
        mc.showImage(mc.oriImg, mc.compImg);
//        mc.showImage(mc.oriImg, procImg);

        long endTime   = System.nanoTime();
        long totalTime = endTime - startTime;
        System.out.println(totalTime/Math.pow(10, 9));
    }


    // 0. read file from image
    /**
     * Read bytes from file, convert it into BufferedImage and store as instance oriImg.
     * Precondition: file size is width = 352, height = 288.
     *
     * @param fileName file to be read
     * @return BufferedImage converted from file content.
     */
    private void readFile(String fileName) {
        FileInputStream input = null;
        byte[] content = new byte[oriWidth * oriHeight];

        try {
            input = new FileInputStream(fileName);
            // Reads up to size bytes of data from this input stream into an array of bytes.
            input.read(content, 0, content.length);

            for (int y = 0; y < oriHeight; y++) {
                for (int x = 0; x < oriWidth; x++) {
                    byte r = content[y * oriWidth + x];
                    byte g = r;
                    byte b = r;

                    int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                    Color color = new Color(pix, true);
                    oriImg.setRGB(x, y, pix);
                }
            }

            input.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // 1. process image, return fully M-divisible image
    /**
     * Process image, if edge not divisible by M or M's square root, pad
     * white pixels to the right (and/or bottom).
     * @param img original image to be processed
     * @return processed img which is divisible by M or M's square root.
     */
    private BufferedImage getProcImage(BufferedImage img) {
        BufferedImage procImg = copyImage(img);
        double sqrtM = Math.sqrt(M);
        int offsetX = 0;
        int offsetY = 0;

        if (isSquare(M)) { // if M is a perfect square, scan by square
            if (img.getWidth() % (int) (sqrtM) != 0) {
                offsetX = (int) (sqrtM) - img.getWidth() % (int) (sqrtM);
            }
            if (img.getHeight() % (int) (sqrtM) != 0) {
                offsetY = (int) (sqrtM) - img.getHeight() % (int) (sqrtM);
            }
        } else { // M is not a perfect square, scan by line
            if (img.getWidth() % M != 0) {
                offsetX = M - img.getWidth() % M;
            }
        }

        procImg = new BufferedImage(img.getWidth() + offsetX, img.getHeight() + offsetY, BufferedImage.TYPE_INT_RGB);

        for (int i = 0; i < procImg.getWidth(); i++) {
            for (int j = 0; j < procImg.getHeight(); j++) {
                byte r = (byte) 255;
                byte g = (byte) 255;
                byte b = (byte) 255;
                int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                procImg.setRGB(i, j, pix);
            }
        }

        for (int i = 0; i < img.getWidth(); i++) {
            for (int j = 0; j < img.getHeight(); j++) {
                procImg.setRGB(i, j, img.getRGB(i, j));
            }
        }

        return procImg;
    }


    // 1.1 helper: copy source image, return copy
    /**
     * Copy BufferedImage.
     * @param source original image to be copied from.
     * @return new BufferedImage which is a copy of the source.
     */
    private static BufferedImage copyImage(BufferedImage source) {
        BufferedImage b = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
        Graphics g = b.getGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return b;
    }


    // 1.2 helper: check if M is a perfect square
    /**
     * Check if M is a perfect square.
     * @param M size of a vector. User input.
     * @return true if M is a perfect square. False otherwise.
     */
    private static boolean isSquare(int M) {
        return Math.ceil(Math.sqrt(M)) == Math.floor(Math.sqrt(M));
    }


    // 2. Turn processed image to a pixel array.
    /**
     * Turn processed image to a pixel array.
     * @param img processed BufferImage.
     * @return an int array which consists of every one-channel pixel of the
     * img, in the order of left to right, top to down.
     */
    private int[] imgToPxs(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int[] allProcPxs = new int[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int px = img.getRGB(x, y);
                Color color = new Color(px, true);
                allProcPxs[y * width + x] = color.getRed();
            }
        }

        return allProcPxs;
    }


    // 3. Convert pixel array to vector representation of the vector space.
    /**
     * Convert pixel array to vector representation of the vector space.
     * @param allPxs an array of one-channel pixels of an image.
     * @param width width of the image.
     * @return an array of vectors of size M.
     */
    private int[][] pxsToVectors(int[] allPxs, int width) { // M is the size of vector
        int height = allPxs.length / width;
        int[][] allVectors = new int[allPxs.length / M][M];

        if (isSquare(M)) { // if M is a perfect square, scan by square
            int blockside = (int) Math.sqrt(M);
            for (int i = 0; i < allVectors.length; i++) {
                for (int j = 0; j < M; j++) {
                    int tmpBRow = i * blockside / width;
                    int tmpBCol = i * blockside % width;
                    int x = tmpBRow * width * blockside + tmpBCol;
                    x += (j / blockside) * width + j % blockside;
                    allVectors[i][j] = allPxs[x];
                }
            }
        } else { // scan by line
            int n = allVectors.length;
            int m = allVectors[0].length;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    allVectors[i][j] = allPxs[i * m + j];
                }
            }
        }

        return allVectors;
    }


    // 4. Initialization of codewords - select N initial codewords.
    /**
     * Randomly select N codewords in all the vectors.
     * @param allVectors all the vectors from the vector space.
     * @return N initial codewords.
     */
    private int[][] getInitCodeWord(int[][] allVectors) {
        int[][] result = new int[N][M];

        for (int i = 0; i < N; i++) {
            int rdIndex = random.nextInt(allVectors.length);
            result[i] = Arrays.copyOf(allVectors[rdIndex], M);
        }

        return result;
    }


    // 5. k-means calculation
    /**
     * Use k-means algorithm to find corresponding codeword for each
     * vector.
     * @param cws initial codewords.
     * @param allVectors all the vectors from the vector space.
     */
    private void kmeans(int[][] cws, int[][] allVectors) {
        groupTags = new int[allVectors.length]; // store each vector's codeword position

        for (int k = 0; k < MAX_ITER_NUM; k++) {
            for (int i = 0; i < groupTags.length; i++) {
                groupTags[i] = findClosestCodewordIndex(allVectors[i], cws);
            }

            int[] cnt = new int[cws.length];
            for (int i = 0; i < groupTags.length; i++) {
                cnt[groupTags[i]] += 1;
            }

            // sum up all pixel vectors under each codeword and get the average of each dimension, thus new codeword.
            int[][] ncws = new int[cws.length][M];

            for (int i = 0; i < allVectors.length; i++) {
                int index = groupTags[i];
                for (int j = 0; j < M; j++) {
                    ncws[index][j] += allVectors[i][j];
                }
            }

            for (int i = 0; i < ncws.length; i++) {
                for (int j = 0; j < M; j++) {
                    if (cnt[i] == 0) {
                        ncws[i][j] = random.nextInt(GREY_SCALE);
                    } else {
                        ncws[i][j] = (int) Math.ceil(ncws[i][j] / cnt[i]);
                    }
                }
            }

            if (hasConverged(cws, ncws, DIFFCAP)) {
                System.out.println("iteration times: " + k);
                break;
            }

            cws = copy2dArray(ncws);
        }

        finalCodewords = copy2dArray(cws);
    }


    // 5.1 helper: findClosestCodewordIndex
    /**
     * Find the closest codeword to the given point.
     * @param vector    to find the closest codeword.
     * @param codewords given list of codewords to find the closest one to
     *                  the given point.
     * @return closest codeword in the list.
     */
    private static int findClosestCodewordIndex(int[] vector,
                                                int[][] codewords) {
        int index = 0;
        double minDist = Double.MAX_VALUE;

        for (int i = 0; i < codewords.length; i++) {
            double curDist = getEuclideanDistance(vector, codewords[i]);
            if (curDist <= minDist) {
                minDist = curDist;
                index = i; // update the index of current closest point in the list.
            }
        }

        return index;
    }


    // 5.2 helper: Calculate Euclidean distance
    /**
     * Calculate the Euclidean distance between two vectors.
     * @param v1 vector 1.
     * @param v2 vector 2.
     * @return Euclidean distance between the two vectors.
     */
    private static double getEuclideanDistance(int[] v1,
                                        int[] v2) {
        double sum = 0.0;
        for (int i = 0; i < v1.length; i++) {
            sum += Math.pow((v1[i] - v2[i]), 2);
        }

        return Math.sqrt(sum);
    }


    // 5.3 helper: copy 2d array
    /**
     * Copy a 2d array.
     * @param arr original array to be copied from.
     * @return copied 2d array.
     */
    private static int[][] copy2dArray(int[][] arr) {
        int[][] result = new int[arr.length][arr[0].length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = Arrays.copyOf(arr[i], arr[0].length);
        }
        return result;
    }


    // 5.4 helper: return false if any distance square between elements in cw and ncw is over diffCap
    /**
     * Check if any distance between corresponding elements in cw and ncw is
     * over the set error.
     * @param cws last codewords.
     * @param ncws new codewords.
     * @param diffCap set maximum error.
     * @return false if any Euclidean distance of corresponding elements in
     * cw and ncw is over diffCap. True otherwise.
     */
    private boolean hasConverged(int[][] cws, int[][] ncws, double diffCap) {
        for (int i = 0; i < cws.length; i++) { // if any distance square is larger than diffCap.
            if (getEuclideanDistance(cws[i], ncws[i]) > diffCap) {
                return false;
            }
        }
        return true;
    }


    // 6. replace all vectors with final codewords
    /**
     * Replace all vectors with final codewords.
     * @param allVectors all vectors in the vector space.
     * @return vectors that are replaced by the final codewords.
     */
    private int[][] encodeVectors(int[][] allVectors) {
        for (int i = 0; i < allVectors.length; i++) {
            allVectors[i] = finalCodewords[groupTags[i]];
        }
        return allVectors;
    }


    // 7. translate replaced vectors to pixel array
    /**
     * Convert vectors to pixel array.
     * @param allVectors all the vectors in the vector space.
     * @param width width of the expected BufferedImage.
     * @return an one-channel pixel array converted from vectors.
     */
    private int[] vectorsToPxs(int[][] allVectors, int width) {
        // create a new array of one channel pixels
        int[] allPxs = new int[allVectors.length * allVectors[0].length];

        // convert vectors back into one channel pixels.
        if (isSquare(M)) { // if M is a perfect square, scan by square
            int blockside = (int) Math.sqrt(M);
            for (int i = 0; i < allVectors.length; i++) {
                for (int j = 0; j < M; j++) {
                    int tmpBRow = i * blockside / width;
                    int tmpBCol = i * blockside % width;
                    int x = tmpBRow * width * blockside + tmpBCol;
                    x += (j / blockside) * width + j % blockside;
                    allPxs[x] = allVectors[i][j];
                }
            }
        } else { // scan by line
            int n = allVectors.length;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < M; j++) {
                    allPxs[i * M + j] = allVectors[i][j];
                }
            }
        }

        return allPxs;
    }


    // 8. convert pixel array to buffered image for display
    /**
     * Convert pixel array to buffered image for display.
     * @param allPxs an array of one-channel pixels.
     * @param procImg processed (padded) BufferedImage.
     */
    private void pxsToImg(int[] allPxs, BufferedImage procImg) {
        // convert one channel pixels back into BufferedImage.
        int tmpWidth = procImg.getWidth();
        int tmpHeight = procImg.getHeight();

        for (int x = 0; x < tmpWidth; x++) {
            for (int y = 0; y < tmpHeight; y++) {
                byte r = (byte) allPxs[tmpWidth * y + x];
                byte g = r;
                byte b = r;
                int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                procImg.setRGB(x, y, pix);
            }
        }

        int width = compImg.getWidth();
        int height = compImg.getHeight();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                compImg.setRGB(x, y, procImg.getRGB(x, y));
            }
        }
    }


    // 9. Final step: Juxtapose original and compressed image
    /**
     * Juxtapose original and compressed image.
     *
     * @param oriImg original image
     * @param comImg compressed image
     */
    private void showImage(BufferedImage oriImg, BufferedImage comImg) {
        // Use labels to display the images
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        GridBagLayout gLayout = new GridBagLayout();
        frame.getContentPane().setLayout(gLayout);

        JLabel lbText1 = new JLabel("Original image (Left)");
        lbText1.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel lbText2 = new JLabel("Image after compression (Right)");
        lbText2.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel lbIm1 = new JLabel(new ImageIcon(oriImg));
        JLabel lbIm2 = new JLabel(new ImageIcon(comImg));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;
        frame.getContentPane().add(lbText1, c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 1;
        c.gridy = 0;
        frame.getContentPane().add(lbText2, c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
        frame.getContentPane().add(lbIm1, c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 1;
        c.gridy = 1;
        frame.getContentPane().add(lbIm2, c);

        frame.pack();
        frame.setVisible(true);
    }
}
