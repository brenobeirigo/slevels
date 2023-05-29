package model.learn;

public record TrainTestConfig(
        String url,
        String
) {
}

"model": {
        "url": "http://localhost:5001",
        "result_folder": "./analysis/data/results/ELU1X/",
        "folder_models": "mnt/d/projects/dev/mobio/notebooks/nyc/model/nn/saved_models",
        "learning_rate": 0.1,
        "discount_factor": 0.5
        },
        "experience_replay": {
        "execution_frequency_timesteps": 30,
        "size_replay_buffer": 500,
        "size_replay_batch": 32
        },
        "target_network": {
        "update_frequency": 30
        },
