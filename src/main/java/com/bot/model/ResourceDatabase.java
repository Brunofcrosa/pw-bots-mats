package com.bot.model;

import com.bot.constants.BotSettings;

import java.io.*;
import java.util.*;

/**
 * Loads and indexes all known resource spawn points from the coords world.txt file,
 * using the resource database (ID -> name/category/level) for enrichment.
 *
 * Provides spatial lookups: find nearest resource to a position, list nearby spawns, etc.
 */
public class ResourceDatabase {

    /* ───── resource template definitions (from database/ files) ───── */
    private static final Map<Integer, String[]> TEMPLATES = new LinkedHashMap<>();

    static {
        // format: ID -> { name, category, level }
        // Wood (Madeira) - type 2861
        put(3074, "Raiz Seca",          "Madeira", 1);
        put(3075, "Tronco Velho",       "Madeira", 2);
        put(3076, "Pilha de Salgueiro", "Madeira", 3);
        put(3077, "Pilha de Pereira",   "Madeira", 4);
        put(3078, "Estaca de Osso de Dragao", "Madeira", 5);

        // Iron (Minerio) - type 2862
        put(3079, "Minerio de Ferro",         "Minerio", 1);
        put(3080, "Minerio de Ferro Negro",   "Minerio", 2);
        put(3081, "Minerio de Ferro-manganes","Minerio", 3);
        put(3082, "Minerio de Ferro Negro",   "Minerio", 4);
        put(3083, "Mina de Ferro Meteorico",  "Minerio", 5);

        // Stone (Pedra) - type 2863
        put(3084, "Mina de Cascalho",         "Pedra", 1);
        put(3085, "Pedreira",                 "Pedra", 2);
        put(3086, "Pedreira de Pedra de Amolar","Pedra", 3);
        put(3087, "Pedreira de Coridon",      "Pedra", 4);
        put(3088, "Pedreira de Grafite",      "Pedra", 5);

        // Coal (Carvao) - type 2864
        put(3089, "Mina de Carvao Bruto",     "Carvao", 1);
        put(3090, "Mina de Carvao",           "Carvao", 2);
        put(3091, "Nucleo de Mina de Carvao", "Carvao", 3);
        put(3092, "Mina de Carvao Vulcanico", "Carvao", 4);
        put(3093, "Mina de Carvao Magmatico", "Carvao", 5);

        // Herbs (Ervas) - type 2865
        put(3094, "Borboleta de Madeira",       "Erva", 4);
        put(3095, "Nectar",                     "Erva", 1);
        put(3096, "Fuling",                     "Erva", 2);
        put(3097, "Shou Wu",                    "Erva", 3);
        put(3098, "Euforbio",                   "Erva", 1);
        put(3099, "Castanheiro-da-india",       "Erva", 2);
        put(3100, "Meimendro",                  "Erva", 3);
        put(3101, "Orvalho do Imortal",         "Erva", 4);
        put(3102, "Fruta Ancia",                "Erva", 2);
        put(3103, "Erva Emenagoga",             "Erva", 1);
        put(3535, "Sulfeto de Arsenico",        "Erva", 2);
        put(3536, "Agerato",                    "Erva", 1);
        put(3537, "Ameixeira de Calice Verde",  "Erva", 3);
        put(3538, "Verme Bastao",               "Erva", 4);
        put(3539, "Ginseng Azul",               "Erva", 3);
        put(3540, "Grama para Cultivo Espiritual","Erva", 2);
        put(3541, "Antidoto",                   "Erva", 3);
        put(3542, "Mu Xiang",                   "Erva", 2);
        put(3543, "Ameixeira de Calice Vermelho","Erva", 4);
        put(3544, "Verme de Nove Odores",       "Erva", 5);
        put(3545, "Agrimonia",                  "Erva", 2);
        put(3546, "Pau-de-Aguia",               "Erva", 3);
        put(3547, "Ameixa de Calice Branco",    "Erva", 5);
        put(3548, "Erva da Serpente",           "Erva", 3);
        put(3549, "Tulipa",                     "Erva", 4);
        put(3550, "Erva Orelha de Tigre",       "Erva", 4);

        // Special
        put(3405, "Cadaver sem Nome", "Especial", 1);
    }

    private static void put(int id, String name, String cat, int level) {
        TEMPLATES.put(id, new String[]{name, cat, String.valueOf(level)});
    }

    /* ───── spawn point data ───── */
    private final List<ResourceSpawn> allSpawns = new ArrayList<>();
    private final Set<Integer> knownResourceIds;

    public ResourceDatabase() {
        knownResourceIds = Collections.unmodifiableSet(TEMPLATES.keySet());
    }

    /**
     * Load spawn points from the coords world.txt file.
     * Only loads entries whose ID exists in our resource template database.
     */
    public int loadFromFile(String filePath) {
        allSpawns.clear();
        int loaded = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(filePath), "UTF-8"))) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 5) continue;
                try {
                    int id = Integer.parseInt(parts[0]);
                    if (!TEMPLATES.containsKey(id)) continue;

                    float x = Float.parseFloat(parts[2]);
                    float y = Float.parseFloat(parts[3]);
                    float z = Float.parseFloat(parts[4]);

                    String[] tpl = TEMPLATES.get(id);
                    allSpawns.add(new ResourceSpawn(id, tpl[0], tpl[1],
                            Integer.parseInt(tpl[2]), x, y, z));
                    loaded++;
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            log("[RES-DB] Erro ao carregar coords: " + e.getMessage());
        }
        log(String.format("[RES-DB] Carregados %d spawn points de recursos (%d tipos)",
                loaded, TEMPLATES.size()));
        return loaded;
    }

    /**
     * Find the nearest known resource spawn to the given position.
     * Returns null if none within maxDist.
     */
    public ResourceSpawn findNearest(float px, float py, float pz, float maxDist) {
        ResourceSpawn best = null;
        float bestDist = maxDist;
        for (ResourceSpawn sp : allSpawns) {
            float d = sp.distanceTo(px, py, pz);
            if (d < bestDist) {
                bestDist = d;
                best = sp;
            }
        }
        return best;
    }

    /**
     * Find all resource spawns within maxDist of the given position.
     * Sorted by distance ascending.
     */
    public List<ResourceSpawn> findNearby(float px, float py, float pz, float maxDist) {
        List<ResourceSpawn> result = new ArrayList<>();
        for (ResourceSpawn sp : allSpawns) {
            if (sp.distanceTo(px, py, pz) <= maxDist) {
                result.add(sp);
            }
        }
        result.sort(Comparator.comparingDouble(s -> s.distanceTo(px, py, pz)));
        return result;
    }

    /**
     * Find all resource spawns of a specific category within maxDist.
     * Categories: "Erva", "Madeira", "Minerio", "Pedra", "Carvao"
     */
    public List<ResourceSpawn> findNearbyByCategory(float px, float py, float pz,
                                                     float maxDist, String category) {
        List<ResourceSpawn> result = new ArrayList<>();
        for (ResourceSpawn sp : allSpawns) {
            if (sp.getCategory().equals(category) && sp.distanceTo(px, py, pz) <= maxDist) {
                result.add(sp);
            }
        }
        result.sort(Comparator.comparingDouble(s -> s.distanceTo(px, py, pz)));
        return result;
    }

    /**
     * Match a detected entity position to a known spawn point.
     * Uses 80m radius — PW resource nodes can spawn with a large offset
     * from their defined spawn center in the npcgen files.
     */
    public ResourceSpawn matchToSpawn(float ex, float ey, float ez) {
        return findNearest(ex, ey, ez, 80.0f);
    }

    /**
     * Get the resource name for a detected entity by matching its position.
     * Returns "Desconhecido" if no match found.
     */
    public String identifyByPosition(float ex, float ey, float ez) {
        ResourceSpawn sp = matchToSpawn(ex, ey, ez);
        return sp != null ? sp.getName() : "Desconhecido";
    }

    /**
     * Get template name by ID.
     */
    public static String getNameById(int templateId) {
        String[] tpl = TEMPLATES.get(templateId);
        return tpl != null ? tpl[0] : null;
    }

    /**
     * Get all known resource template IDs.
     */
    public Set<Integer> getKnownResourceIds() {
        return knownResourceIds;
    }

    /**
     * Get total loaded spawn count.
     */
    public int getSpawnCount() {
        return allSpawns.size();
    }

    /**
     * Get all spawns (unmodifiable).
     */
    public List<ResourceSpawn> getAllSpawns() {
        return Collections.unmodifiableList(allSpawns);
    }

    private static void log(String msg) {
        System.out.println(msg);
        BotSettings.logToUi(msg);
    }
}
