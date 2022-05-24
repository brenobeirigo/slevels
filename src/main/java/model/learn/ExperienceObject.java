package model.learn;

import config.Config;
import dao.Dao;
import dao.ServerUtil;

public class ExperienceObject {
    public int id;
    public DecisionSpaceObject state;
    public DecisionSpaceObject actions;
    public RewardObject reward;
    public ExperienceObject(DecisionSpaceObject preDecisionSpaceObj, DecisionSpaceObject postDecisionStateSpaceObj, RewardObject reward) {
        this.id = preDecisionSpaceObj.hashCode();
        this.state = preDecisionSpaceObj;
        this.actions = postDecisionStateSpaceObj;
        this.reward = reward;

    }

    public void remember() {
        String r = ServerUtil.postJsonObjectToURL(this, Dao.getInstance().getServer().ADDRESS_SERVER + "/remember/");
        System.out.println(r);
    }
}
