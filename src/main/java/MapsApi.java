import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class MapsApi {
    private static final String COMMA = "%2C";
    private static final double MIN_DISTANCE = 50d;
    private static final double MAX_DISTANCE = 40d;


    public static void main(String[] args) {
        Coordinate origin = new Coordinate(12.93175,77.6287);
        Coordinate des = new Coordinate(12.92662, 77.63696);
        String url = "https://maps.googleapis.com/maps/api/directions/json?origin="+origin.getLat()+COMMA+origin.getLng()+"&destination="+des.getLat()+COMMA+des.getLng()+"&key=AIzaSyAEQvKUVouPDENLkQlCF6AAap1Ze-6zMos";


        HttpResponse<String> response = getResult(url);
        if (response == null){
            return;
        }
        JSONArray stepsArr = getStepsPolyline(response.body());
        if (stepsArr == null){
            return;
        }
        List<Coordinate> coordinates = new ArrayList<>();

        for (int i=0; i<stepsArr.length(); i++){
            try {
                String points = stepsArr.getJSONObject(i).getJSONObject("polyline").getString("points");
                //removing last due to redundancy
                if (coordinates.size()>0){
                    coordinates.remove(coordinates.size()-1);
                }
                coordinates.addAll(decodePoly(points));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }


        List<Coordinate> resultCoordinates = getRequiredCoordinates(origin, coordinates);
        resultCoordinates.add(des);

        System.out.println("Results : ");
        for (Coordinate c :resultCoordinates){
            System.out.println(c);
        }
    }

    private static List<Coordinate> getRequiredCoordinates(Coordinate origin, List<Coordinate> coordinateList){
        Coordinate point1 = origin;
        List<Coordinate> resultCoordinates = new ArrayList<>();
        resultCoordinates.add(origin);

        double dis = 0d;
        for (int i=0; i< coordinateList.size(); i++) {
            dis += getDistanceBetweenPoint(point1, coordinateList.get(i));

            if (dis >= MIN_DISTANCE) {
                while (dis>MIN_DISTANCE){
                    Coordinate mid = getPointCloseToMaxDis(dis, point1, coordinateList.get(i));

                    resultCoordinates.add(mid);
                    dis = dis - MIN_DISTANCE;
                    point1 = mid;
                }
                point1 = coordinateList.get(i);
            }else
                point1 = coordinateList.get(i);

        }
        return resultCoordinates;
    }

    static HttpResponse<String> getResult(String url){
        try {
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString() );

        } catch (URISyntaxException | InterruptedException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }
  /*Get polylines inside steps*/
    static JSONArray getStepsPolyline(String body){
        try {
            JSONObject jsonObject = new JSONObject(body);
            JSONArray routes = jsonObject.getJSONArray("routes");
            JSONObject routeObj = routes.getJSONObject(0);
            JSONObject legsObj= routeObj.getJSONArray("legs").getJSONObject(0);

            return legsObj.getJSONArray("steps");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* Decode polyline encoded string into List of Coordinates */
    private static List<Coordinate> decodePoly(String encoded) {
        List<Coordinate> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            Coordinate p = new Coordinate(((double)lat / 1E5), ((double) lng / 1E5));
            poly.add(p);
        }
        return poly;
    }

    /**
     * using heversine formula to calculate distance between two points
     * @param p1 Coordinate
     * @param p2 Coordinate
     * @return double
     */
    public static double getDistanceBetweenPoint(Coordinate p1, Coordinate p2) {
        double lat1 = p1.getLat();
        double lon1 = p1.getLng();
        double lat2 = p2.getLat();
        double lon2 = p2.getLng();

        var R = 6371; // Radius of the earth in km
        double dLat = deg2rad(lat2-lat1);  // deg2rad below
        double dLon = deg2rad(lon2-lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) *
                                Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = (2 * R * Math.asin(Math.min(1.0, Math.sqrt(a))));
        return (c *1000);// result in meter
    }

    /* The function to convert decimal  into radians */
    private static double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    /**
     * Finding mid point of Coordinates
     * @param p1 Coordinate
     * @param p2 Coordinate
     * @return new Cordinates
     */
    private static Coordinate getMidPoint(Coordinate p1, Coordinate p2){
        double newLat = (p1.getLat() +p2.getLat())/2;
        double newLng = (p1.getLng() +p2.getLng())/2;
        return new Coordinate(newLat, newLng);
    }

   /*Get points which are close to 50m which is the max_distance*/
    private static Coordinate getPointCloseToMaxDis(double dis, Coordinate p1, Coordinate p2){
        Coordinate mid = getMidPoint(p1, p2);
        if(dis < MIN_DISTANCE && dis> MAX_DISTANCE){
            return mid;
        }
        if(dis>MIN_DISTANCE){
            dis -= getDistanceBetweenPoint(p1, mid);
            return getPointCloseToMaxDis(dis, p1, mid);

        }else {
            dis += getDistanceBetweenPoint(p1, mid);
            return getPointCloseToMaxDis(dis, mid, p2);
        }

    }
}
