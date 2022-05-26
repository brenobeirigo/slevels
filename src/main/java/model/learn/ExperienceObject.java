package model.learn;

import config.InstanceConfig;
import dao.Dao;
import dao.ServerUtil;

public class ExperienceObject {
    public int id;
    public FleetStateActionSpaceObject state_action;
    public FleetStateActionSpaceObject post_decision_state_action;
    public FleetStateActionRewardObject state_action_reward;

    public ExperienceObject(FleetStateActionSpaceObject preDecisionSpaceObj, FleetStateActionSpaceObject postDecisionStateSpaceObj, FleetStateActionRewardObject state_action_reward) {
        this.id = preDecisionSpaceObj.hashCode();
        this.state_action = preDecisionSpaceObj;
        this.state_action_reward = state_action_reward;
        this.post_decision_state_action = postDecisionStateSpaceObj;

    }

    public void remember(InstanceConfig.LearningConfig learningConfig) {


        String r = ServerUtil.postJsonObjectToURL(this, Dao.getInstance().getServer().ADDRESS_SERVER + "/remember/" + learningConfig.getExperiencesFolder());
        System.out.println(r);
    }
}
