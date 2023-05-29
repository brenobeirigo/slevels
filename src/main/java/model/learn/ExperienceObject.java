package model.learn;

import dao.Dao;
import dao.ServerUtil;
import model.Vehicle;
import org.springframework.web.util.UriUtils;
import simulation.matching.ResultAssignment;

import java.util.Set;

public class ExperienceObject {
    public int id;
    public FleetStateActionSpaceObject state_action;
    public FleetStateActionSpaceObject post_decision_state_action;
    public FleetStateActionRewardObject state_action_reward;

    public ExperienceObject(
            FleetStateActionSpaceObject preDecisionSpaceObj,
            FleetStateActionSpaceObject postDecisionStateSpaceObj) {

        this.id = preDecisionSpaceObj.hashCode();
        this.state_action = preDecisionSpaceObj;
        this.post_decision_state_action = postDecisionStateSpaceObj;
    }

    public void updateStateActionReward(Set<Vehicle> vehicles, ResultAssignment resultAssignment){
        this.state_action_reward = new FleetStateActionRewardObject(
                vehicles,
                resultAssignment);
    }

    public ExperienceObject(
            FleetStateActionSpaceObject preDecisionSpaceObj,
            FleetStateActionSpaceObject postDecisionStateSpaceObj,
            FleetStateActionRewardObject state_action_reward) {

        this(preDecisionSpaceObj, postDecisionStateSpaceObj);
        this.state_action_reward = state_action_reward;
    }

    public String rememberFile(LearningSettings learningSettings) {
        String url = Dao.getInstance().getServer().ADDRESS_SERVER + "/remember/" + UriUtils.encode(learningSettings.getExperiencesFolder(), "UTF-8");
        String msg = ServerUtil.postJsonObjectToURL(this, url);
        return msg;
    }


    public String remember(LearningSettings learningSettings) {
        String msg = ServerUtil.postJsonObjectToURL(this, Dao.getInstance().getServer().rememberURL);
        return msg;
    }
}
