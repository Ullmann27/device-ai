import at.heinz.deviceai.avatar.*;
import at.heinz.deviceai.avatar3d.*;
public class AvatarBaselineProbe {
  static AvatarPose pose(Viseme v) { return new AvatarPose(AvatarState.SPEAKING, AvatarExpression.NEUTRAL,0f,0f,.6f,v,1f,.4f,false); }
  public static void main(String[] args) {
    AvatarMorphMapper mapper=new AvatarMorphMapper(AvatarGlbContract.INSTANCE.getTARGETS());
    mapper.update(pose(Viseme.AA),1f,true);
    mapper.update(pose(Viseme.PP),1.016f,true);
    float aa=mapper.getWeights()[AvatarGlbContract.INSTANCE.getTARGETS().indexOf("AA")];
    float jaw=mapper.getWeights()[AvatarGlbContract.INSTANCE.getTARGETS().indexOf("JawOpen")];
    System.out.println("AA_TO_PP rendered_AA="+aa+" extra_jaw="+jaw+" shared_opening="+(.65f/.8f*aa)+" total_common_opening="+(.65f/.8f*aa+jaw));
    AvatarPoseReducer reducer=new AvatarPoseReducer();
    reducer.apply(new AvatarEvent.SpeechStarted("probe","synthetic",100L));
    reducer.apply(new AvatarEvent.AudioLevel("probe",.9f,200L,AudioLevelSource.PCM_ENVELOPE));
    reducer.apply(new AvatarEvent.AudioLevel("probe",.1f,250L,AudioLevelSource.PCM_ENVELOPE));
    float before=reducer.pose(260L).getSpeakEnergy();
    reducer.apply(new AvatarEvent.AudioLevel("probe",.9f,200L,AudioLevelSource.PCM_ENVELOPE));
    System.out.println("STALE_AUDIO energy_before="+before+" after_replay="+reducer.pose(260L).getSpeakEnergy());
  }
}
