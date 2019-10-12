package uk.ac.nott.cs.g53dia.agent;
import uk.ac.nott.cs.g53dia.library.*;

import java.util.*;

import static java.lang.Float.min;
import static java.lang.StrictMath.abs;
import static uk.ac.nott.cs.g53dia.agent.CourseworkTanker.ActionType.*;

public class CourseworkTanker extends Tanker {

	public CourseworkTanker() {
		this(new Random());
	}

	public CourseworkTanker(Random r) {
		this.r = r;
		randomizeExplorationDirection();
	}

	class Emplacement {
		Point point;
		int X = 0;
		int Y = 0;
		Task task;
	}

	private Set<Point> mappedPoints = new HashSet<>();
	private Set<Emplacement> fuelPumpLocationsSet = new HashSet<>();
	private Set<Emplacement> stationLocationsSet = new HashSet<>();
	private Set<Emplacement> wellLocationsSet = new HashSet<>();
	private Emplacement tankerAgent = new Emplacement();
	private ActionType explorationDirection;
	enum EmplacementOption {FUEL_PUMP, STATION, WELL}
	enum ActionType {
		NORTH(0),
		SOUTH(1),
		EAST(2),
		WEST(3),
		NORTHEAST(4),
		NORTHWEST(5),
		SOUTHEAST(6),
		SOUTHWEST(7),
		REFUEL(8),
		DISPOSE_WASTE(9),
		LOAD_WASTE(10);

		int value;
		ActionType(int value) {
			this.value = value;
		}
	}

	public Action senseAndAct(Cell[][] view, boolean actionFailed, long timestep) {
		buildMap(view);

		ActionType actionType = arbiter();

		switch (actionType) {
			case REFUEL:
				randomizeExplorationDirection();
				return new RefuelAction();
			case DISPOSE_WASTE:
				return new DisposeWasteAction();
			case LOAD_WASTE:
				Emplacement closestStation = getClosestEmplacementPoint(EmplacementOption.STATION, tankerAgent);
				return new LoadWasteAction(Objects.requireNonNull(closestStation).task);
			default:
				updateCurrentPosition(actionType);
				return new MoveAction(actionType.value);
		}
	}

	private void buildMap(Cell[][] view) {
		for (int i = 0; i < 41; i++) {
			for (int j = 0; j < 41; j++) {
				Cell checkCell = view[i][j];
				if (!mappedPoints.contains(checkCell.getPoint())) {
					mappedPoints.add(checkCell.getPoint());
					addToMap(checkCell, tankerAgent.X + i, tankerAgent.Y - j);
				} else if (checkCell instanceof Station) {
					updateStationStatus(checkCell);
				}
			}
		}
	}

	private void addToMap(Cell checkCell, int i, int j) {
		if (!(checkCell instanceof EmptyCell)) {
			Point point = checkCell.getPoint();
			Emplacement emplacement = new Emplacement();
			emplacement.X = i - 20;
			emplacement.Y = j + 20;

			emplacement.point = point;
			if (checkCell instanceof FuelPump) {
				fuelPumpLocationsSet.add(emplacement);
			} else if (checkCell instanceof Station) {
				emplacement.task = ((Station) checkCell).getTask();
				stationLocationsSet.add(emplacement);
			} else if (checkCell instanceof Well) {
				wellLocationsSet.add(emplacement);
			}
		}
	}

	private void updateStationStatus(Cell checkCell) {
		if (!stationLocationsSet.isEmpty()) {
			for (Emplacement temp : stationLocationsSet) {
				if (temp.point == checkCell.getPoint()) {
					temp.task = ((Station) checkCell).getTask();
					return;
				}
			}
		}
	}

	private ActionType arbiter() {
		Emplacement closestStation = getClosestEmplacementPoint(EmplacementOption.STATION, tankerAgent);
		if (isRefuelNeeded()) {
			return getRefuelAction();
		} else if (closestStation != null)  {
			return getStandardProcedureAction();
		} else {
			return explorationDirection;
		}
	}

	private Emplacement getClosestEmplacementPoint(EmplacementOption name, Emplacement current) {
		switch (name) {
			case FUEL_PUMP: return getEmplacement(current, fuelPumpLocationsSet, true);
			case STATION: return getEmplacement(current, stationLocationsSet, false);
			case WELL: return getEmplacement(current, wellLocationsSet, true);
			default: return null;
		}
	}

	private Emplacement getEmplacement(Emplacement current, Set<Emplacement> fuelPumpLocationsSet, boolean allowNullTask) {
		int distance = Integer.MAX_VALUE;
		Emplacement closestEmplacement = null;
		if (!fuelPumpLocationsSet.isEmpty()) {
			for (Emplacement temp : fuelPumpLocationsSet) {
				if (getDistance(current, temp) < distance && (temp.task != null || allowNullTask)) {
					distance = getDistance(current, temp);
					closestEmplacement = temp;
				}
			}
		}
		return closestEmplacement;
	}

	private int getDistance(Emplacement current, Emplacement target) {
		int xDifference = abs(target.X - current.X);
		int yDifference = abs(target.Y - current.Y);
		return (int) ((xDifference + yDifference) - min(xDifference, yDifference));
	}

	private boolean isRefuelNeeded() {
		Emplacement closestFuelPump = getClosestEmplacementPoint(EmplacementOption.FUEL_PUMP, tankerAgent);
		return getDistance(tankerAgent, Objects.requireNonNull(closestFuelPump)) > (getFuelLevel() / 2) - 4;
	}

	private ActionType getRefuelAction() {
		return getAction(EmplacementOption.FUEL_PUMP, REFUEL);
	}

	private ActionType getAction(EmplacementOption emplacementOption, ActionType defaultAction) {
		Emplacement closestWell = getClosestEmplacementPoint(emplacementOption, tankerAgent);
		int distanceToWell = getDistance(tankerAgent, Objects.requireNonNull(closestWell));
		return distanceToWell > 0 ? getDirectionToEmplacement(closestWell) : defaultAction;
	}

	private ActionType getDirectionToEmplacement(Emplacement emplacementPoint) {
		ActionType actionType = null;
		int xDifference = emplacementPoint.X - tankerAgent.X;
		int yDifference = emplacementPoint.Y - tankerAgent.Y;

		if (yDifference > 0) {
			actionType = NORTH;
		}
		else if (yDifference < 0) {
			actionType = SOUTH;
		}

		if (xDifference > 0) {
			if (actionType == NORTH) {
				actionType = NORTHEAST;
			}
			else if (actionType == SOUTH) {
				actionType = SOUTHEAST;
			}
			else {
				actionType = EAST;
			}
		}
		else if (xDifference < 0) {
			if (actionType == NORTH) {
				actionType = NORTHWEST;
			}
			else if (actionType == SOUTH) {
				actionType = SOUTHWEST;
			}
			else {
				actionType = WEST;
			}
		}

		return actionType;
	}

	private ActionType getStandardProcedureAction() {
		int explorationFactor = 100;
		if (!wellLocationsSet.isEmpty() && isActionFeasible(EmplacementOption.WELL) && isWasteDisposalNeeded()) {
			return getDisposeWasteAction();
		} else if (!stationLocationsSet.isEmpty() && isActionFeasible(EmplacementOption.STATION) && !isWasteDisposalNeeded()) {
			return getCollectWasteAction();
		} else if (getFuelLevel() > explorationFactor) {
			randomizeExplorationDirection();
			return explorationDirection;
		} else {
			return getRefuelAction();
		}
	}

	private boolean isActionFeasible(EmplacementOption targetName) {
		Emplacement closestEmplacementPoint = getClosestEmplacementPoint(targetName, tankerAgent);
		if (closestEmplacementPoint != null) {
			return getFuelLevel() / 2 > getDistance(closestEmplacementPoint, Objects.requireNonNull(getClosestEmplacementPoint
					(EmplacementOption.FUEL_PUMP, closestEmplacementPoint))) + getDistance(tankerAgent, closestEmplacementPoint);
		}
			return false;
	}

	private boolean isWasteDisposalNeeded() {
		double wasteNeededFactor = 0.66;
		return getWasteLevel() > MAX_WASTE * wasteNeededFactor;
	}

	private ActionType getDisposeWasteAction() {
		return getAction(EmplacementOption.WELL, DISPOSE_WASTE);
	}

	private ActionType getCollectWasteAction() {
		return getAction(EmplacementOption.STATION, LOAD_WASTE);
	}

	private void randomizeExplorationDirection() {
		explorationDirection = ActionType.values()[r.nextInt(8)];
	}

	private void updateCurrentPosition(ActionType actionType) {
		switch (actionType) {
			case NORTH:
				tankerAgent.Y++;
				break;
			case SOUTH:
				tankerAgent.Y--;
				break;
			case EAST:
				tankerAgent.X++;
				break;
			case WEST:
				tankerAgent.X--;
				break;
			case NORTHEAST:
				tankerAgent.Y++;
				tankerAgent.X++;
				break;
			case NORTHWEST:
				tankerAgent.Y++;
				tankerAgent.X--;
				break;
			case SOUTHEAST:
				tankerAgent.Y--;
				tankerAgent.X++;
				break;
			case SOUTHWEST:
				tankerAgent.Y--;
				tankerAgent.X--;
				break;
		}
	}

}