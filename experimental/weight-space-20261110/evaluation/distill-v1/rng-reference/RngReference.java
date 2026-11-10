class RngReference {
    public static void main(String[] args) {
        java.util.Random random = new java.util.Random(20261110L);
        for (int i = 0; i < 12; i++) {
            System.out.println(random.nextInt(64));
        }
    }
}
