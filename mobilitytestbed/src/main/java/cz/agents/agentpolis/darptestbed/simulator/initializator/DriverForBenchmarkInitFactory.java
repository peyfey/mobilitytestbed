package cz.agents.agentpolis.darptestbed.simulator.initializator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import com.google.inject.Injector;

import cz.agents.agentpolis.darptestbed.global.GlobalParams;
import cz.agents.agentpolis.darptestbed.global.Utils;
import cz.agents.agentpolis.darptestbed.siminfrastructure.communication.passenger.protocol.PassengerMessageProtocol;
import cz.agents.agentpolis.darptestbed.siminfrastructure.logger.VehicleMoveLogger;
import cz.agents.agentpolis.darptestbed.siminfrastructure.request.Driver;
import cz.agents.agentpolis.darptestbed.siminfrastructure.request.GPS;
import cz.agents.agentpolis.darptestbed.simmodel.agent.driver.DecentDriverAgent;
import cz.agents.agentpolis.darptestbed.simmodel.agent.driver.DriverAgentFactory;
import cz.agents.agentpolis.darptestbed.simmodel.agent.driver.logic.DriverCentrLogic;
import cz.agents.agentpolis.darptestbed.simmodel.agent.driver.logic.DriverDecentrLogic;
import cz.agents.agentpolis.darptestbed.simmodel.agent.driver.logic.DriverDecentrLogicExample;
import cz.agents.agentpolis.darptestbed.simmodel.agent.exception.WrongSettingsException;
import cz.agents.agentpolis.darptestbed.simmodel.agent.timer.Timer;
import cz.agents.agentpolis.darptestbed.simmodel.entity.vehicle.TestbedVehicle;
import cz.agents.agentpolis.darptestbed.simmodel.environment.model.TestbedModel;
import cz.agents.agentpolis.darptestbed.simmodel.environment.model.TestbedVehicleStorage;
import cz.agents.agentpolis.darptestbed.simulator.initializator.osm.NodeExtendedFunction;
import cz.agents.agentpolis.simmodel.agent.Agent;
import cz.agents.agentpolis.simmodel.agent.activity.movement.VehicleDrivingActivity;
import cz.agents.agentpolis.simmodel.entity.vehicle.VehicleType;
import cz.agents.agentpolis.simmodel.environment.model.AgentPositionModel;
import cz.agents.agentpolis.simmodel.environment.model.VehiclePositionModel;
import cz.agents.agentpolis.simmodel.environment.model.VehicleStorage;
import cz.agents.agentpolis.simmodel.environment.model.citymodel.transportnetwork.AllNetworkNodes;
import cz.agents.agentpolis.simmodel.environment.model.citymodel.transportnetwork.EGraphType;
import cz.agents.agentpolis.simmodel.environment.model.entityvelocitymodel.EntityVelocityModel;
import cz.agents.agentpolis.simmodel.environment.model.query.AgentPositionQuery;
import cz.agents.agentpolis.simulator.creator.initializator.AgentInitFactory;
import cz.agents.agentpolis.utils.convertor.VelocityConvertor;
import eu.superhub.wp4.model.simodel.environment.model.vehiclemodel.VehicleDataModel;
import eu.superhub.wp4.model.simodel.environment.model.vehiclemodel.VehicleTemplate;
import eu.superhub.wp4.model.simodel.environment.model.vehiclemodel.VehicleTemplateId;

public class DriverForBenchmarkInitFactory implements AgentInitFactory {

	private static final Logger LOGGER = Logger.getLogger(DriverForBenchmarkInitFactory.class);
	private final File serializedDriverPopulation;
	private final int driverLimit;

	public DriverForBenchmarkInitFactory(File serializedDriverPopulation, int driverLimit) {
		super();
		this.serializedDriverPopulation = serializedDriverPopulation;
		this.driverLimit = driverLimit;

	}

	public DriverForBenchmarkInitFactory(File serializedDriverPopulation) {
		super();
		this.serializedDriverPopulation = serializedDriverPopulation;
		this.driverLimit = Integer.MAX_VALUE;

	}

	@Override
	public List<Agent> initAllAgentLifeCycles(Injector injector) {

		Timer taxiDriversTimer = injector.getInstance(TestbedModel.class).getTaxiDriversTimer();

		boolean centralized = GlobalParams.isCentralized();

		// get ready for creating a logic (I couldn't figure out any better
		// place to hide this code)
		TestbedModel taxiModel = injector.getInstance(TestbedModel.class);
		AgentPositionQuery positionQuery = injector.getInstance(AgentPositionQuery.class);
		AllNetworkNodes allNetworkNodes = injector.getInstance(AllNetworkNodes.class);
		Utils utils = injector.getInstance(Utils.class);
		NodeExtendedFunction nearestNodeFinder = injector.getInstance(NodeExtendedFunction.class);
		VehicleDataModel vehicleDataModel = injector.getInstance(VehicleDataModel.class);
		Random random = injector.getInstance(Random.class);

		Map<Integer, Integer> seatsDistribution = new HashMap<Integer, Integer>();
		seatsDistribution.put(5, GlobalParams.getNumberOfFiveSeatVehicles());
		seatsDistribution.put(6, GlobalParams.getNumberOfSixSeatVehicles());
		seatsDistribution.put(7, GlobalParams.getNumberOfSevenSeatVehicles());

		List<Agent> agents = new ArrayList<Agent>();
		DriverAgentFactory factory = new DriverAgentFactory();

		double velocityOfVehicle = VelocityConvertor.kmph2mps(GlobalParams.getVelocityInKmph());

		int counter = 0;
		List<Driver> drivers = loadSerializedPassengerPopulation(serializedDriverPopulation);
		Collections.shuffle(drivers, injector.getInstance(Random.class));
		for (Driver driver : drivers) {

			if (counter++ > driverLimit) {
				break;
			}

			TestbedVehicle vehicle = new TestbedVehicle("Taxi_owned_by_" + driver.driverId, VehicleType.CAR, 5.0,
					driver.vehicleCapacity, EGraphType.HIGHWAY, driver.vehicleEquipments);

			VehicleTemplate vehicleTemplate = selectVehicleTemplate(vehicleDataModel, VehicleType.CAR, random);
			vehicleDataModel.assineVehilceTemplate(vehicle.getId(), vehicleTemplate.vehicleTemplateId);

			long initialLocation = findNearestNode(driver.driverInitPosition, nearestNodeFinder);

			injector.getInstance(VehicleStorage.class).addEntity(vehicle);
			injector.getInstance(TestbedVehicleStorage.class).addEntity(vehicle);
			injector.getInstance(VehiclePositionModel.class).setNewEntityPosition(vehicle.getId(), initialLocation);
			injector.getInstance(EntityVelocityModel.class).addEntityMaxVelocity(vehicle.getId(), velocityOfVehicle);

			String agentId = driver.driverId;
			VehicleDrivingActivity drivingActivity = injector.getInstance(VehicleDrivingActivity.class);

			Agent driverAgent = null;

			if (centralized) {
				// centralized algorithms
				DriverCentrLogic logic = null;
				PassengerMessageProtocol sender = injector.getInstance(PassengerMessageProtocol.class);

				switch (GlobalParams.getCentralAlgType()) {
				//case 1:
				//	break;
				//case 2:
				//	break;
				//case 3:
				//	break;
				default:
					// by default, load the DriverCentrLogic class for drivers
					logic = new DriverCentrLogic(agentId, sender, taxiModel, positionQuery, allNetworkNodes, utils,
							vehicle, drivingActivity);
					break;
				}

				driverAgent = factory.createCentrDriverAgent(agentId, logic, injector);

			} else {
				// decentralized algorithms
				DriverDecentrLogic logic = null;
				PassengerMessageProtocol sender = injector.getInstance(PassengerMessageProtocol.class);
				switch (GlobalParams.getDecentrAlgType()) {
				//case 1:
				//	break;
				//case 2:
				//	break;
				//case 3:
				//	break;
				default:
					// by default, load the DriverDecentrLogicExample class for the drivers
					logic = new DriverDecentrLogicExample(agentId, sender, taxiModel, positionQuery,
							allNetworkNodes, utils, vehicle, drivingActivity);
					break;
				}

				DecentDriverAgent decentDriverAgent = factory.createDecentDriverAgent(agentId, logic, injector);
				taxiDriversTimer.addCallback(decentDriverAgent);

				driverAgent = decentDriverAgent;

			}

			injector.getInstance(AgentPositionModel.class).setNewEntityPosition(driverAgent.getId(), initialLocation);
			injector.getInstance(TestbedModel.class).addFreeTaxi(vehicle.getId(), driverAgent.getId());
			injector.getInstance(VehicleMoveLogger.class).logVehicleMove(vehicle.getId(), initialLocation);

			agents.add(driverAgent);

		}

		LOGGER.info("Taxi drivers have been created. The number of created taxi agents is: " + agents.size());
		return agents;
	}

	private long findNearestNode(GPS gps, NodeExtendedFunction nearestNodeFinder) {
		return nearestNodeFinder.getNearestNodeByNodeId(gps.longitude, gps.latitude);
	}

	public List<Driver> loadSerializedPassengerPopulation(File serializedPopulation) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.readValue(serializedPopulation, new TypeReference<List<Driver>>() {
			});
		} catch (IOException e) {
			LOGGER.error(e);
		}

		return new ArrayList<Driver>();

	}

	private VehicleTemplate selectVehicleTemplate(VehicleDataModel vehicleDataModel, VehicleType vehicleType,
			Random random) {

		TreeMap<Double, VehicleTemplateId> dist = vehicleDataModel.getDistributionForVehicleType(vehicleType);
		Double key = dist.ceilingKey(random.nextDouble());
		VehicleTemplateId vehicleTemplateId = dist.get(key);

		return vehicleDataModel.getVehicleTemplate(vehicleTemplateId);

	}

}