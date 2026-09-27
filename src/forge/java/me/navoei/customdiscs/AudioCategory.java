package me.navoei.customdiscs;

public enum AudioCategory {
    MUSIC_DISC(0),
    HORN(1),
    PLAYER_HEAD(2);

    private final int id;

    AudioCategory(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static AudioCategory fromId(int id) {
        for (AudioCategory category : values()) {
            if (category.id == id) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown audio category: " + id);
    }
}
