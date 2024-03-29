"""
    Application logic for parkwizard
"""
import os
import json
from elasticsearch import Elasticsearch, RequestsHttpConnection, TransportError
from requests_aws4auth import AWS4Auth
from django.http import HttpResponse
from django.views.decorators.http import require_GET, require_POST
from django.views.decorators.csrf import csrf_exempt
from django.conf import settings
from . import esindex

#everything aws about this project
CONFIG_FILE = os.path.join(settings.BASE_DIR, "parkwizard", "config.json")

def load_config(filename, service):
    """
        load aws configuration
    """
    config = None
    with open(filename) as handle:
        config = json.load(handle)
    return config[service]

AWS_CONFIG = load_config(CONFIG_FILE, "aws")
AWS_AUTH = AWS4Auth(AWS_CONFIG['access_key'], AWS_CONFIG['secret_key'],
                    AWS_CONFIG["region"], AWS_CONFIG["service"])


# Global Elasticsearch object
ES = Elasticsearch(hosts=[{'host': AWS_CONFIG["es_node"], 'port': 443}],
                   http_auth=AWS_AUTH,
                   use_ssl=True,
                   verify_certs=True,
                   connection_class=RequestsHttpConnection)

#Setup elasticsearch indices on loading this module
esindex.setup(ES)

@require_POST
@csrf_exempt
def addparking(request):
    """
        Allow users to report parking location
    """
    # create a parking index if does not exists already
    response = {"success": True}
    try:
        user = request.POST['id']
        location = {
            "lat": request.POST["lat"],
            "lon": request.POST["lon"]
        }
        parking = {
            'name': request.POST['name'],
            'location': location,
            'spots': int(request.POST['spots']),
            'available': int(request.POST['spots'])
        }

        response = esindex.add_parking(ES, user, parking)

        return HttpResponse(json.dumps(message),
                            content_type="application/json")

    except KeyError as error:
        return HttpResponse(json.dumps(error),
                            status=500)

    except TransportError as error:
        return HttpResponse(json.dumps(error.error), status=500, content_type="application/json")


@require_GET
def searchparking(request):
    """
        Get available parking locations
    """
    response = dict()
    status = 200
    try:
        user = request.GET["id"]
        location = {
            "lat": request.GET["lat"],
            "lon": request.GET["lon"]
        }
        #search 500 meters range
        radius = "500m"
        cost = 5 #cost of valid search of parking
        response = esindex.search_parking(ES, user, cost, location, radius)

    except (KeyError, TransportError) as error:
        response = {
            "success": False,
            "message": error,
            "parkings": []
        }
        status = 500
    return HttpResponse(json.dumps(response), content_type="application/json",
                        status=status)


@require_POST
@csrf_exempt
def adduser(request):
    """
        New User registers to system
    """
    message = {"success": True}
    try:
        user_id = request.POST["id"]
        name = request.POST["name"]
        record = {
            "name": name,
            "score": 100
        }
        message = esindex.add_user(ES, user_id, record)
        return HttpResponse(json.dumps(message),
                            content_type="application/json")

    except (KeyError, Exception):
        message["success"] = False
        status = 500
        return HttpResponse(json.dumps(message), status=status,
                            content_type="application/json")

@require_GET
def getscore(request):
    """
        get user profile data
    """
    message = {"success": True}
    try:
        user_id = request.GET['id']
        score = esindex.getscore(ES, user_id)
        return HttpResponse(json.dumps(score), status=200,
                            content_type="application/json")

    except (KeyError, ValueError, TransportError):
        message["success"] = False
        return HttpResponse(json.dumps(message), status=500,
                            content_type="application/json")

@require_GET
def getupdatelocations(request):
    """
        get locations in 50m radius that can be updated by the user
    """
    status = 200
    response = {
        "success": True,
        "message": "Parking locations found !",
        "parkings": []
    }
    try:
        user = request.GET["id"]
        location = {
            "lat": request.GET["lat"],
            "lon": request.GET["lon"]
        }
        #search 50 meters range
        radius = "100m"
        parkings = list()
        results = esindex.__search_parking(ES, location, 0, radius)
        if len(results) < 1:
            response['message'] = 'No nearby parking to update !'
        # parse results
        for result in results:
            record = dict()
            record['locid'] = result['_id']
            record['name'] = result['_source']['name']
            record['location'] = result['_source']['location']
            parkings.append(record)
        response['parkings'] = parkings

    except (KeyError, TransportError) as error:
        response["success"] = False
        response["message"] = error
        status = 500
    return HttpResponse(json.dumps(response), content_type="application/json",
                        status=status)

@require_POST
@csrf_exempt
def updateparking(request):
    """
        update a parking spot
        penalise user if he/she gives bad information
    """
    status = 200
    response = {"success": True}
    try:
        user_id = request.POST['id']
        available = int(request.POST["available"])
        locid = request.POST["locid"]
        response = esindex.updateparking(ES, user_id, locid, available)
    except (KeyError, TransportError) as error:
        response["success"] = False
        response["message"] = error
        status = 500
    return HttpResponse(json.dumps(response), content_type="application/json",
                        status=status)
