export interface TrainingAlert {
  userId: string
  name: string
  status: string
  nextTrainingDate: string | null
  lastTrainedIt: string | null
}
