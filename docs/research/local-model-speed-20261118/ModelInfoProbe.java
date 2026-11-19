import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.LogSeverity;
import com.google.ai.edge.litertlm.Modality;
import com.google.ai.edge.litertlm.ModelInfo;

/** HOST probe: prints what LiteRT-LM 0.18.0 ModelInfo reports for a .litertlm file. No inference is run. */
public final class ModelInfoProbe {
    public static void main(String[] args) throws Exception {
        Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);
        for (String path : args) {
            try (ModelInfo info = ModelInfo.Companion.from(path)) {
                System.out.println("FILE " + new java.io.File(path).getName() + " bytes=" + new java.io.File(path).length());
                System.out.println("  modelType=" + info.getModelType());
                System.out.println("  inputModalities=" + info.inputModalities());
                System.out.println("  maxContextTokens=" + info.maxContextTokens() + " dynamicContext=" + info.isDynamicContext());
                System.out.println("  minRuntimeVersion=" + info.minRuntimeVersion());
                for (Modality modality : Modality.values()) {
                    try {
                        System.out.println("  supportedBackends(" + modality + ")=" + info.supportedBackends(modality)
                            + " npuBrand=" + info.npuBrand(modality) + " soc=" + info.socName(modality));
                    } catch (Throwable failure) {
                        System.out.println("  supportedBackends(" + modality + ") -> " + failure.getClass().getSimpleName()
                            + ": " + failure.getMessage());
                    }
                }
                if (info instanceof ModelInfo.Llm llm) {
                    System.out.println("  supportsFunctionCalling=" + llm.supportsFunctionCalling());
                    System.out.println("  supportsThinking=" + llm.supportsThinking());
                    System.out.println("  hasSpeculativeDecodingSupport=" + llm.hasSpeculativeDecodingSupport());
                    System.out.println("  defaultSamplerParams=" + llm.defaultSamplerParams());
                }
            }
        }
    }
}
