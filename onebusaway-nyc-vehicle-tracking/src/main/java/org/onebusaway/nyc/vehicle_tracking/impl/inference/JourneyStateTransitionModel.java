package org.onebusaway.nyc.vehicle_tracking.impl.inference;

import java.util.Arrays;
import java.util.List;

import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.BlockState;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.EdgeState;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.JourneyStartState;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.JourneyState;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.MotionState;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.VehicleState;
import org.onebusaway.nyc.vehicle_tracking.impl.particlefilter.CDFMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JourneyStateTransitionModel {

  private VehicleStateSensorModel _vehicleStateSensorModel;

  private BlockStateTransitionModel _blockStateTransitionModel;

  @Autowired
  public void setVehicleStateSensorModel(
      VehicleStateSensorModel vehicleStateSensorModel) {
    _vehicleStateSensorModel = vehicleStateSensorModel;
  }

  @Autowired
  public void setBlockStateTransitionModel(
      BlockStateTransitionModel blockStateTransitionModel) {
    _blockStateTransitionModel = blockStateTransitionModel;
  }

  /****
   * 
   * 
   ****/

  public VehicleState move(VehicleState parentState, EdgeState edgeState,
      MotionState motionState, Observation obs) {

    List<JourneyState> journeyStates = getTransitionJourneyStates(parentState,
        obs);

    return sampleJourneyState(parentState, edgeState, motionState,
        journeyStates, obs);
  }

  public List<JourneyState> getTransitionJourneyStates(
      VehicleState parentState, Observation obs) {

    JourneyState parentJourneyState = parentState.getJourneyState();

    switch (parentJourneyState.getPhase()) {
      case AT_BASE:
        return moveAtBase(obs);
      case DEADHEAD_BEFORE:
        return moveDeadheadBefore(parentJourneyState);
      case LAYOVER_BEFORE:
        return moveLayoverBefore(obs);
      case IN_PROGRESS:
        return moveInProgress(obs);
      case DEADHEAD_DURING:
        return moveDeadheadDuring(parentJourneyState);
      case LAYOVER_DURING:
        return moveLayoverDuring(obs);
      case DEADHEAD_AFTER:
        return moveDeadheadAfter(obs);
      case LAYOVER_AFTER:
        return moveLayoverAfter(obs);
      default:
        throw new IllegalStateException("unknown journey state: "
            + parentJourneyState.getPhase());
    }
  }

  private VehicleState sampleJourneyState(VehicleState parentState,
      EdgeState edgeState, MotionState motionState,
      List<JourneyState> journeyStates, Observation obs) {

    CDFMap<VehicleState> cdf = new CDFMap<VehicleState>();

    for (JourneyState journeyState : journeyStates) {

      BlockState blockState = _blockStateTransitionModel.transitionBlockState(
          parentState, motionState, journeyState, obs);

      VehicleState vehicleState = new VehicleState(edgeState, motionState,
          blockState, journeyState, obs);

      double p = _vehicleStateSensorModel.likelihood(parentState, vehicleState,
          obs);

      cdf.put(p, vehicleState);
    }

    return cdf.sample();
  }

  private List<JourneyState> moveAtBase(Observation obs) {

    return Arrays.asList(JourneyState.atBase(), JourneyState.layoverBefore(),
        JourneyState.deadheadBefore(obs.getLocation()),
        JourneyState.inProgress());
  }

  private List<JourneyState> moveDeadheadBefore(JourneyState parentJourneyState) {

    JourneyStartState start = parentJourneyState.getData();

    return Arrays.asList(JourneyState.atBase(), JourneyState.layoverBefore(),
        JourneyState.deadheadBefore(start.getJourneyStart()),
        JourneyState.inProgress());
  }

  private List<JourneyState> moveLayoverBefore(Observation obs) {

    return Arrays.asList(JourneyState.atBase(), JourneyState.layoverBefore(),
        JourneyState.deadheadBefore(obs.getLocation()),
        JourneyState.inProgress());
  }

  private List<JourneyState> moveInProgress(Observation obs) {

    return Arrays.asList(JourneyState.inProgress(),
        JourneyState.deadheadDuring(obs.getLocation()),
        JourneyState.layoverDuring(), JourneyState.deadheadAfter(),
        JourneyState.layoverAfter());
  }

  private List<JourneyState> moveDeadheadDuring(JourneyState parentJourneyState) {

    JourneyStartState start = parentJourneyState.getData();

    return Arrays.asList(JourneyState.inProgress(),
        JourneyState.deadheadDuring(start.getJourneyStart()),
        JourneyState.layoverDuring());
  }

  private List<JourneyState> moveLayoverDuring(Observation obs) {

    return Arrays.asList(JourneyState.inProgress(),
        JourneyState.deadheadDuring(obs.getLocation()),
        JourneyState.layoverDuring());
  }

  private List<JourneyState> moveDeadheadAfter(Observation obs) {

    return Arrays.asList(JourneyState.atBase(),
        JourneyState.deadheadBefore(obs.getLocation()),
        JourneyState.inProgress(), JourneyState.deadheadAfter(),
        JourneyState.layoverAfter());
  }

  private List<JourneyState> moveLayoverAfter(Observation obs) {

    return Arrays.asList(JourneyState.atBase(),
        JourneyState.deadheadBefore(obs.getLocation()),
        JourneyState.inProgress(), JourneyState.deadheadAfter(),
        JourneyState.layoverAfter());
  }
}
