import at.heinz.deviceai.ui.ContinuumHologramRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Export original geometric parameters. */
public final class ExportOriginalOrb {
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    public static void main(String[] args) throws Exception {
        var root = Path.of(args[0]);
        var renderer = new ContinuumHologramRenderer();
        renderer.setGeometry(ContinuumHologramRenderer.Geometry.Companion.getREFERENCE_COCKPIT());
        var plexus = field(renderer, "plexus");
        var result = new JSONObject().put("source", "ContinuumHologramRenderer.REFERENCE_COCKPIT")
            .put("evidence", "COMPILED_ORIGINAL_PARAMETERS_NOT_SCREEN_PIXELS");
        var geometry = new JSONObject();
        for (String name : List.of("centerY", "radiusOfWidth", "radiusOfHeight", "pad", "wall", "flat", "sphere", "haloNodes"))
            geometry.put(name, field(renderer.getGeometry(), name));
        result.put("geometry", geometry);
        var orbits = new JSONArray();
        for (var orbit : (List<?>)field(renderer, "orbits")) {
            var item = new JSONObject();
            for (String name : List.of("tilt", "a", "b", "speed", "width")) item.put(name, field(orbit, name));
            item.put("colors", new JSONArray(field(orbit, "colors")));
            orbits.put(item);
        }
        result.put("orbits", orbits);
        var cloud = new JSONObject();
        for (String name : List.of("bx", "by", "bz", "edgeA", "edgeB", "dx", "dy", "dz", "dustTint", "dustSize",
                                   "anx", "any", "anz", "arcRadius", "arcStart", "arcSweep", "arcSpeed"))
            cloud.put(name, new JSONArray(field(plexus, name)));
        var layers = new JSONArray();
        for (var value : (Object[])field(plexus, "layer")) layers.put(value.toString());
        cloud.put("layer", layers);
        result.put("plexus", cloud);
        var hashes = new JSONObject();
        var digest = MessageDigest.getInstance("SHA-256");
        for (String name : List.of("ContinuumHologramRenderer.kt", "ContinuumHologramPlexus.kt", "ContinuumOrbitalRing.kt")) {
            String relative = "app/src/main/java/at/heinz/deviceai/ui/"+name;
            hashes.put(relative, HexFormat.of().formatHex(digest.digest(Files.readAllBytes(root.resolve(relative)))));
        }
        result.put("source_hashes", hashes);
        Files.writeString(Path.of(args[1]), result.toString(2)+"\n");
        System.out.println("ORIGINAL_ORB_EXPORTED " + orbits.length()+" orbits; "+layers.length()+" nodes");
    }
}
