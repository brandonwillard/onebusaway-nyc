package org.onebusaway.nyc.presentation.impl.realtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.onebusaway.geospatial.model.CoordinateBounds;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.nyc.presentation.impl.DateUtil;
import org.onebusaway.nyc.presentation.impl.realtime.SiriSupportV2.Filters;
import org.onebusaway.nyc.presentation.impl.realtime.SiriSupportV2.OnwardCallsMode;
import org.onebusaway.nyc.presentation.service.realtime.PresentationService;
import org.onebusaway.nyc.presentation.service.realtime.RealtimeServiceV2;
import org.onebusaway.nyc.transit_data.services.NycTransitDataService;
import org.onebusaway.nyc.transit_data_federation.siri.SiriExtensionWrapper;
import org.onebusaway.nyc.transit_data_federation.siri.SiriJsonSerializerV2;
import org.onebusaway.nyc.transit_data_federation.siri.SiriXmlSerializerV2;
import org.onebusaway.realtime.api.TimepointPredictionRecord;
import org.onebusaway.transit_data.model.ArrivalAndDepartureBean;
import org.onebusaway.transit_data.model.ArrivalsAndDeparturesQueryBean;
import org.onebusaway.transit_data.model.ListBean;
import org.onebusaway.transit_data.model.RouteBean;
import org.onebusaway.transit_data.model.SearchQueryBean;
import org.onebusaway.transit_data.model.StopBean;
import org.onebusaway.transit_data.model.StopGroupBean;
import org.onebusaway.transit_data.model.StopGroupingBean;
import org.onebusaway.transit_data.model.StopWithArrivalsAndDeparturesBean;
import org.onebusaway.transit_data.model.StopsBean;
import org.onebusaway.transit_data.model.StopsForRouteBean;
import org.onebusaway.transit_data.model.service_alerts.ServiceAlertBean;
import org.onebusaway.transit_data.model.service_alerts.SituationQueryBean;
import org.onebusaway.transit_data.model.trips.TripBean;
import org.onebusaway.transit_data.model.trips.TripDetailsBean;
import org.onebusaway.transit_data.model.trips.TripDetailsInclusionBean;
import org.onebusaway.transit_data.model.trips.TripForVehicleQueryBean;
import org.onebusaway.transit_data.model.trips.TripStatusBean;
import org.onebusaway.transit_data.model.trips.TripsForRouteQueryBean;
import org.onebusaway.transit_data_federation.services.AgencyAndIdLibrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.org.siri.siri_2.AnnotatedStopPointStructure;
import uk.org.siri.siri_2.LineDirectionStructure;
import uk.org.siri.siri_2.MonitoredStopVisitStructure;
import uk.org.siri.siri_2.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri_2.VehicleActivityStructure;
import uk.org.siri.siri_2.VehicleActivityStructure.MonitoredVehicleJourney;

/**
 * A source of SIRI classes containing real time data, subject to the
 * conventions expressed in the PresentationService.
 * 
 * @author jmaki
 *
 */
@Component
public class RealtimeServiceV2Impl implements RealtimeServiceV2 {

	private NycTransitDataService _nycTransitDataService;

	private PresentationService _presentationService;

	private SiriXmlSerializerV2 _siriXmlSerializer = new SiriXmlSerializerV2();

	private SiriJsonSerializerV2 _siriJsonSerializer = new SiriJsonSerializerV2();

	private Long _now = null;

	@Override
	public void setTime(long time) {
		_now = time;
		_presentationService.setTime(time);
	}

	public long getTime() {
		if (_now != null)
			return _now;
		else
			return System.currentTimeMillis();
	}

	@Autowired
	public void setNycTransitDataService(
			NycTransitDataService transitDataService) {
		_nycTransitDataService = transitDataService;
	}

	@Autowired
	public void setPresentationService(PresentationService presentationService) {
		_presentationService = presentationService;
	}

	@Override
	public PresentationService getPresentationService() {
		return _presentationService;
	}

	@Override
	public SiriJsonSerializerV2 getSiriJsonSerializer() {
		return _siriJsonSerializer;
	}

	@Override
	public SiriXmlSerializerV2 getSiriXmlSerializer() {
		return _siriXmlSerializer;
	}

	/**
	 * SIRI METHODS
	 */
	@Override
	public List<VehicleActivityStructure> getVehicleActivityForRoute(
			String routeId, String directionId, int maximumOnwardCalls,
			long currentTime) {
		List<VehicleActivityStructure> output = new ArrayList<VehicleActivityStructure>();

		boolean useTimePredictionsIfAvailable = _presentationService
				.useTimePredictionsIfAvailable();

		ListBean<TripDetailsBean> trips = getAllTripsForRoute(routeId,
				currentTime);
		for (TripDetailsBean tripDetails : trips.getList()) {
			// filter out interlined routes
			if (routeId != null
					&& !tripDetails.getTrip().getRoute().getId()
							.equals(routeId))
				continue;

			// filtered out by user
			if (directionId != null
					&& !tripDetails.getTrip().getDirectionId()
							.equals(directionId))
				continue;

			if (!_presentationService.include(tripDetails.getStatus()))
				continue;

			VehicleActivityStructure activity = new VehicleActivityStructure();
			activity.setRecordedAtTime(DateUtil
					.toXmlGregorianCalendar(tripDetails.getStatus()
							.getLastUpdateTime()));

			List<TimepointPredictionRecord> timePredictionRecords = null;
			if (useTimePredictionsIfAvailable == true) {
				timePredictionRecords = _nycTransitDataService
						.getPredictionRecordsForTrip(AgencyAndId
								.convertFromString(routeId).getAgencyId(),
								tripDetails.getStatus());
			}

			activity.setMonitoredVehicleJourney(new MonitoredVehicleJourney());
			SiriSupportV2.fillMonitoredVehicleJourney(
					activity.getMonitoredVehicleJourney(),
					tripDetails.getTrip(), tripDetails.getStatus(), null,
					OnwardCallsMode.VEHICLE_MONITORING, _presentationService,
					_nycTransitDataService, maximumOnwardCalls,
					timePredictionRecords, currentTime);

			output.add(activity);
		}

		Collections.sort(output, new Comparator<VehicleActivityStructure>() {
			public int compare(VehicleActivityStructure arg0,
					VehicleActivityStructure arg1) {
				try {
					SiriExtensionWrapper wrapper0 = (SiriExtensionWrapper) arg0
							.getMonitoredVehicleJourney().getMonitoredCall()
							.getExtensions().getAny();
					SiriExtensionWrapper wrapper1 = (SiriExtensionWrapper) arg1
							.getMonitoredVehicleJourney().getMonitoredCall()
							.getExtensions().getAny();
					return wrapper0
							.getDistances()
							.getDistanceFromCall()
							.compareTo(
									wrapper1.getDistances()
											.getDistanceFromCall());
				} catch (Exception e) {
					return -1;
				}
			}
		});

		return output;
	}

	@Override
	public VehicleActivityStructure getVehicleActivityForVehicle(
			String vehicleId, int maximumOnwardCalls, long currentTime) {

		boolean useTimePredictionsIfAvailable = _presentationService
				.useTimePredictionsIfAvailable();

		TripForVehicleQueryBean query = new TripForVehicleQueryBean();
		query.setTime(new Date(currentTime));
		query.setVehicleId(vehicleId);

		TripDetailsInclusionBean inclusion = new TripDetailsInclusionBean();
		inclusion.setIncludeTripStatus(true);
		inclusion.setIncludeTripBean(true);
		query.setInclusion(inclusion);

		TripDetailsBean tripDetailsForCurrentTrip = _nycTransitDataService
				.getTripDetailsForVehicleAndTime(query);
		if (tripDetailsForCurrentTrip != null) {
			if (!_presentationService.include(tripDetailsForCurrentTrip
					.getStatus()))
				return null;

			VehicleActivityStructure output = new VehicleActivityStructure();
			output.setRecordedAtTime(DateUtil
					.toXmlGregorianCalendar(tripDetailsForCurrentTrip
							.getStatus().getLastUpdateTime()));

			List<TimepointPredictionRecord> timePredictionRecords = null;
			if (useTimePredictionsIfAvailable == true) {
				timePredictionRecords = _nycTransitDataService
						.getPredictionRecordsForTrip(AgencyAndId
								.convertFromString(vehicleId).getAgencyId(),
								tripDetailsForCurrentTrip.getStatus());
			}

			output.setMonitoredVehicleJourney(new MonitoredVehicleJourney());
			SiriSupportV2.fillMonitoredVehicleJourney(
					output.getMonitoredVehicleJourney(),
					tripDetailsForCurrentTrip.getTrip(),
					tripDetailsForCurrentTrip.getStatus(), null,
					OnwardCallsMode.VEHICLE_MONITORING, _presentationService,
					_nycTransitDataService, maximumOnwardCalls,
					timePredictionRecords, currentTime);

			return output;
		}

		return null;
	}

	@Override
	public List<MonitoredStopVisitStructure> getMonitoredStopVisitsForStop(
			String stopId, int maximumOnwardCalls, long currentTime) {
		List<MonitoredStopVisitStructure> output = new ArrayList<MonitoredStopVisitStructure>();

		boolean useTimePredictionsIfAvailable = _presentationService
				.useTimePredictionsIfAvailable();

		for (ArrivalAndDepartureBean adBean : getArrivalsAndDeparturesForStop(
				stopId, currentTime)) {
			TripStatusBean statusBeanForCurrentTrip = adBean.getTripStatus();
			TripBean tripBeanForAd = adBean.getTrip();

			if (statusBeanForCurrentTrip == null)
				continue;

			if (!_presentationService.include(statusBeanForCurrentTrip)
					|| !_presentationService.include(adBean,
							statusBeanForCurrentTrip))
				continue;

			MonitoredStopVisitStructure stopVisit = new MonitoredStopVisitStructure();
			stopVisit.setRecordedAtTime(DateUtil
					.toXmlGregorianCalendar(statusBeanForCurrentTrip
							.getLastUpdateTime()));

			List<TimepointPredictionRecord> timePredictionRecords = null;
			if (useTimePredictionsIfAvailable == true) {
				timePredictionRecords = _nycTransitDataService
						.getPredictionRecordsForTrip(AgencyAndId
								.convertFromString(stopId).getAgencyId(),
								statusBeanForCurrentTrip);
			}

			stopVisit
					.setMonitoredVehicleJourney(new MonitoredVehicleJourneyStructure());
			SiriSupportV2.fillMonitoredVehicleJourney(
					stopVisit.getMonitoredVehicleJourney(), tripBeanForAd,
					statusBeanForCurrentTrip, adBean.getStop(),
					OnwardCallsMode.STOP_MONITORING, _presentationService,
					_nycTransitDataService, maximumOnwardCalls,
					timePredictionRecords, currentTime);

			output.add(stopVisit);
		}

		Collections.sort(output, new Comparator<MonitoredStopVisitStructure>() {
			public int compare(MonitoredStopVisitStructure arg0,
					MonitoredStopVisitStructure arg1) {
				try {
					SiriExtensionWrapper wrapper0 = (SiriExtensionWrapper) arg0
							.getMonitoredVehicleJourney().getMonitoredCall()
							.getExtensions().getAny();
					SiriExtensionWrapper wrapper1 = (SiriExtensionWrapper) arg1
							.getMonitoredVehicleJourney().getMonitoredCall()
							.getExtensions().getAny();
					return wrapper0
							.getDistances()
							.getDistanceFromCall()
							.compareTo(
									wrapper1.getDistances()
											.getDistanceFromCall());
				} catch (Exception e) {
					return -1;
				}
			}
		});

		return output;
	}

	@Override
	public Map<Boolean, List<AnnotatedStopPointStructure>> getAnnotatedStopPointStructures(
			CoordinateBounds bounds, String detailLevel, long currentTime,
			Map<Filters, String> filters) {
		
		// A list of stops that will go in our search results
		List<StopBean> stopsForResults = new ArrayList<StopBean>();
		
		// Keep track of which routes are already in our search results by direction
		Map<String,  Map<String,Boolean>> routesUpcomingService = new HashMap<String, Map<String,Boolean>>();
		
		// Cache stops by route so we don't need to call the transit data service repeatedly for the same route
		Map<String, StopsForRouteBean> stopsForRouteLookup = new HashMap<String, StopsForRouteBean>();
		
		// Store processed StopBean as AnnotatedStopPointStructure 
		List<AnnotatedStopPointStructure> annotatedStopPoints = new ArrayList<AnnotatedStopPointStructure>();
		
		Map<Boolean, List<AnnotatedStopPointStructure>> output = new HashMap<Boolean, List<AnnotatedStopPointStructure>>();
		
		Boolean upcomingServiceAllStops = null;  
		
		
		for (StopBean stopBean : getStopsForBounds(bounds, currentTime)) {
			
			// Record of routes by direction id for this stop
			Map<String, List<RouteBean>> routesByDirection = new HashMap<String, List<RouteBean>>();
			
			
			for (RouteBean route : stopBean.getRoutes()) {
				// route is a route serving the current stopBeanForSearchResult

				// Query for all stops on this route
				StopsForRouteBean stopsForRoute = stopsForRouteLookup.get(route.getId());
				if (stopsForRoute == null) {
					stopsForRoute = _nycTransitDataService.getStopsForRoute(route.getId());
					stopsForRouteLookup.put(route.getId(), stopsForRoute);
				}

				// Get the groups of stops on this route. The id of each group corresponds to a GTFS direction id for this route.
				for (StopGroupingBean stopGrouping : stopsForRoute.getStopGroupings()) {
					for (StopGroupBean stopGroup : stopGrouping.getStopGroups()) {

						String directionId = stopGroup.getId();
						
						Boolean hasUpcomingScheduledService = _nycTransitDataService
								.stopHasUpcomingScheduledService((route
										.getAgency() != null ? route
										.getAgency().getId() : null), 
								System.currentTimeMillis(), 
								stopBean.getId(), 
								route.getId(), 
								directionId
							);
						
						// filter hasUpcomingScheduledService
						String filterUpcomingScheduledService = filters.get(Filters.UPCOMING_SCHEDULED_SERVICE);
						
						if (StringUtils.isNotBlank(filterUpcomingScheduledService)
								&& !String.valueOf(hasUpcomingScheduledService)
										.equalsIgnoreCase(filterUpcomingScheduledService.trim()) || !hasUpcomingScheduledService) {
							continue;
						}
						
						if(upcomingServiceAllStops == null)
							upcomingServiceAllStops = hasUpcomingScheduledService;
						
						// Check if the current stop is served in this direction. If so, record it.
						if (stopGroup.getStopIds().contains(stopBean.getId())) {
							if (!routesByDirection.containsKey(directionId)) {
								routesByDirection.put(directionId, new ArrayList<RouteBean>());
							}
							routesByDirection.get(directionId).add(route);
						}
					}
				}
			}
			
			AnnotatedStopPointStructure annotatedStopPoint = new AnnotatedStopPointStructure();
			boolean isValid = SiriSupportV2.fillAnnotatedStopPointStructure(annotatedStopPoint,
					stopBean, routesByDirection, filters, currentTime);
			if(isValid)
				annotatedStopPoints.add(annotatedStopPoint);
		}
		output.put(upcomingServiceAllStops, annotatedStopPoints);
		return output;
			
			/*// Iterate over routes binned by direction for this stop and compare to routes by direction already in our search results
			boolean shouldAddStopToResults = false;
			for (Map.Entry<String, List<RouteBean>> entry : routesByDirection.entrySet()) {
				String directionId = entry.getKey();
				List<RouteBean> routesForThisDirection = entry.getValue();

				if (!routesByDirectionAlreadyInResults.containsKey(directionId)) {
					routesByDirectionAlreadyInResults.put(directionId, new ArrayList<RouteBean>());
				}

				@SuppressWarnings("unchecked")
				List<RouteBean> additionalRoutes = ListUtils.subtract(routesForThisDirection, routesByDirectionAlreadyInResults.get(directionId));
				if (additionalRoutes.size() > 0) {
					// This stop is contributing new routes in this direction, so add these additional
					// stops to our record of stops by direction already in search results and toggle
					// flag that tells to to add the stop to the search results.
					routesByDirectionAlreadyInResults.get(directionId).addAll(additionalRoutes);
					// We use this flag because we want to add new routes to our record potentially for each
					// direction id, but we only want to add the stop to the search results once. It happens below.
					shouldAddStopToResults = true;
				}
			}
			if (shouldAddStopToResults) {
				// Add the stop to our search results
				stopsForResults.add(stopBean);
			}
			// Break out of iterating through stops if we've reached our max
			if (stopsForResults.size() >= 200) {
				break;
			}
			
		}*/
		
		/*for (StopBean stop : stopsForResults) {
			AnnotatedStopPointStructure annotatedStopPoint = new AnnotatedStopPointStructure();
			boolean isValid = SiriSupportV2.fillAnnotatedStopPointStructure(annotatedStopPoint,
					stop, filters, detailLevel, currentTime);
			if(isValid)
				output.add(annotatedStopPoint);
		}*/
		
		//filterStopPoints(annotatedStopPoint, filters, output);
		

		/*
		 * Collections.sort(output, new
		 * Comparator<MonitoredStopVisitStructure>() { public int
		 * compare(MonitoredStopVisitStructure arg0, MonitoredStopVisitStructure
		 * arg1) { try { SiriExtensionWrapper wrapper0 =
		 * (SiriExtensionWrapper)arg0
		 * .getMonitoredVehicleJourney().getMonitoredCall
		 * ().getExtensions().getAny(); SiriExtensionWrapper wrapper1 =
		 * (SiriExtensionWrapper
		 * )arg1.getMonitoredVehicleJourney().getMonitoredCall
		 * ().getExtensions().getAny(); return
		 * wrapper0.getDistances().getDistanceFromCall
		 * ().compareTo(wrapper1.getDistances().getDistanceFromCall()); }
		 * catch(Exception e) { return -1; } } });
		 */
	}

	@Override
	public List<AnnotatedStopPointStructure> getAnnotatedStopPointStructures(
			List<AgencyAndId> routeIds, String detailLevel, long currentTime,
			Map<Filters, String> filters) {
		List<AnnotatedStopPointStructure> output = new ArrayList<AnnotatedStopPointStructure>();

		for (StopBean stopBean : getStopsForRoutes(routeIds, currentTime)) {

			AnnotatedStopPointStructure annotatedStopPoint = new AnnotatedStopPointStructure();

			/*boolean isValid = SiriSupportV2.fillAnnotatedStopPointStructure(annotatedStopPoint,
					stopBean, filters, currentTime);*/
			
			boolean isValid = true;
			
			if(isValid)
				output.add(annotatedStopPoint);
		}
		return output;
	}

	/**
	 * CURRENT IN-SERVICE VEHICLE STATUS FOR ROUTE
	 */

	/**
	 * Returns true if there are vehicles in service for given route+direction
	 */
	@Override
	public boolean getVehiclesInServiceForRoute(String routeId,
			String directionId, long currentTime) {
		ListBean<TripDetailsBean> trips = getAllTripsForRoute(routeId,
				currentTime);
		for (TripDetailsBean tripDetails : trips.getList()) {
			// filter out interlined routes
			if (routeId != null
					&& !tripDetails.getTrip().getRoute().getId()
							.equals(routeId))
				continue;

			// filtered out by user
			if (directionId != null
					&& !tripDetails.getTrip().getDirectionId()
							.equals(directionId))
				continue;

			if (!_presentationService.include(tripDetails.getStatus()))
				continue;

			return true;
		}

		return false;
	}

	/**
	 * Returns true if there are vehicles in service for given route+direction
	 * that will stop at the indicated stop in the future.
	 */
	@Override
	public boolean getVehiclesInServiceForStopAndRoute(String stopId,
			String routeId, long currentTime) {
		for (ArrivalAndDepartureBean adBean : getArrivalsAndDeparturesForStop(
				stopId, currentTime)) {
			TripStatusBean statusBean = adBean.getTripStatus();
			if (!_presentationService.include(statusBean)
					|| !_presentationService.include(adBean, statusBean))
				continue;

			// filtered out by user
			if (routeId != null
					&& !adBean.getTrip().getRoute().getId().equals(routeId))
				continue;

			return true;
		}

		return false;
	}

	/**
	 * SERVICE ALERTS METHODS
	 */

	@Override
	public List<ServiceAlertBean> getServiceAlertsForRoute(String routeId) {
		return getServiceAlertsForRouteAndDirection(routeId, null);
	}

	@Override
	public List<ServiceAlertBean> getServiceAlertsForRouteAndDirection(
			String routeId, String directionId) {
		SituationQueryBean query = new SituationQueryBean();
		SituationQueryBean.AffectsBean affects = new SituationQueryBean.AffectsBean();
		query.getAffects().add(affects);

		affects.setRouteId(routeId);
		if (directionId != null) {
			affects.setDirectionId(directionId);
		} else {
			/*
			 * TODO The route index is not currently being populated correctly;
			 * query by route and direction, and supply both directions if not
			 * present
			 */
			SituationQueryBean.AffectsBean affects1 = new SituationQueryBean.AffectsBean();
			query.getAffects().add(affects1);
			affects1.setRouteId(routeId);
			affects1.setDirectionId("0");
			SituationQueryBean.AffectsBean affects2 = new SituationQueryBean.AffectsBean();
			query.getAffects().add(affects2);
			affects2.setRouteId(routeId);
			affects2.setDirectionId("1");
		}

		ListBean<ServiceAlertBean> serviceAlerts = _nycTransitDataService
				.getServiceAlerts(query);
		return serviceAlerts.getList();
	}

	@Override
	public List<ServiceAlertBean> getServiceAlertsGlobal() {
		SituationQueryBean query = new SituationQueryBean();
		SituationQueryBean.AffectsBean affects = new SituationQueryBean.AffectsBean();

		affects.setAgencyId("__ALL_OPERATORS__");
		query.getAffects().add(affects);

		ListBean<ServiceAlertBean> serviceAlerts = _nycTransitDataService
				.getServiceAlerts(query);
		return serviceAlerts.getList();
	}

	/**
	 * PRIVATE METHODS
	 */
	private ListBean<TripDetailsBean> getAllTripsForRoute(String routeId,
			long currentTime) {
		TripsForRouteQueryBean tripRouteQueryBean = new TripsForRouteQueryBean();
		tripRouteQueryBean.setRouteId(routeId);
		tripRouteQueryBean.setTime(currentTime);

		TripDetailsInclusionBean inclusionBean = new TripDetailsInclusionBean();
		inclusionBean.setIncludeTripBean(true);
		inclusionBean.setIncludeTripStatus(true);
		tripRouteQueryBean.setInclusion(inclusionBean);

		return _nycTransitDataService.getTripsForRoute(tripRouteQueryBean);
	}

	private List<ArrivalAndDepartureBean> getArrivalsAndDeparturesForStop(
			String stopId, long currentTime) {
		ArrivalsAndDeparturesQueryBean query = new ArrivalsAndDeparturesQueryBean();
		query.setTime(currentTime);
		query.setMinutesBefore(5 * 60);
		query.setMinutesAfter(5 * 60);

		StopWithArrivalsAndDeparturesBean stopWithArrivalsAndDepartures = _nycTransitDataService
				.getStopWithArrivalsAndDepartures(stopId, query);

		return stopWithArrivalsAndDepartures.getArrivalsAndDepartures();
	}

	private List<StopBean> getStopsForBounds(CoordinateBounds bounds,
			long currentTime) {
		if (bounds != null) {
			SearchQueryBean queryBean = new SearchQueryBean();
			queryBean.setType(SearchQueryBean.EQueryType.BOUNDS_OR_CLOSEST);
			queryBean.setBounds(bounds);
			queryBean.setMaxCount(100);

			StopsBean stops = _nycTransitDataService.getStops(queryBean);
			return stops.getStops();
		}
		return new ArrayList<StopBean>();
	}

	private List<StopBean> getStopsForRoutes(List<AgencyAndId> routeIds,
			long currentTime) {
		Set<StopBean> stopsSet = new HashSet<StopBean>();
		List<StopBean> stopsList = new ArrayList<StopBean>();

		for (AgencyAndId routeId : routeIds) {
			StopsForRouteBean stopsForRoute = _nycTransitDataService
					.getStopsForRoute(AgencyAndIdLibrary
							.convertToString(routeId));
			stopsSet.addAll(stopsForRoute.getStops());
		}
		stopsList.addAll(stopsSet);

		return stopsList;
	}
	
	/*private void filterStopPoints(AnnotatedStopPointStructure annotatedStopPoint, Map<Filters, String> filters, List<AnnotatedStopPointStructure> output){
		List<LineDirectionStructure> lineDirections = (List<LineDirectionStructure>) (Object) annotatedStopPoint
				.getLines().getLineRefOrLineDirection();

		for (LineDirectionStructure lineDirection : lineDirections) {
			
			String fiterRouteId = filters.get(Filters.LINE_REF);
			String filterDirectionId = filters.get(Filters.DIRECTION_REF);
			
			String thisRouteId = lineDirection.getLineRef().getValue();
			String thisDirectionId = lineDirection.getDirectionRef()
					.getValue();

			// user filtering
			if (StringUtils.isNotBlank(fiterRouteId)
					&& !fiterRouteId.equalsIgnoreCase(thisRouteId))
				continue;
			if (StringUtils.isNotBlank(null) && !thisDirectionId.equals(filterDirectionId))
				continue;
			
			output.add(annotatedStopPoint);
		}
	}*/

}