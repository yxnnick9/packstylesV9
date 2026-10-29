package de.yxnnick9.studio;

/** Built-in galaxy panoramas (4096x2048 equirectangular, generated for this mod). */
final class Skies {
    private Skies() {
    }

    static final String[] NAMES = {
            "Starlight", "Milky Way", "Polar Night", "Moonlit Lake",
            "Violet Nebula", "Pink Dream", "Cyan Rift", "Magenta Core", "Orion Blue", "Rose Quartz", "Cosmic Dust", "Deep Space",
            "Northern Lights", "Emerald Curtain", "Glacial Ribbon", "Violet Aurora",
            "Golden Hour", "Peach Horizon", "Crimson Dusk", "Purple Sunset",
            "Pure Midnight", "Soft Navy", "Slate Horizon", "Quiet Teal"
    };
    static final String[] FAMILY = {
            "Stars", "Stars", "Stars", "Stars",
            "Nebula", "Nebula", "Nebula", "Nebula", "Nebula", "Nebula", "Nebula", "Nebula",
            "Aurora", "Aurora", "Aurora", "Aurora",
            "Sunset", "Sunset", "Sunset", "Sunset",
            "Minimal", "Minimal", "Minimal", "Minimal"
    };
    static final int COUNT = NAMES.length;

    static String resource(int index) {
        return String.format("/assets/yxnnick9studio/skies/sky_%02d.jpg", index + 1);
    }
}
