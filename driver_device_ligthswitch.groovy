preferences {
	input("email", "text", title: "E-mail", description: "Your neviweb® account login e-mail")
	input("password", "password", title: "Password", description: "Your neviweb® account login password")
	input("gatewayname", "text", title: "Network Name:", description: "Name of your neviweb® network")
	input("devicename", "text", title: "Device Name:", description: "Name of your neviweb® thermostat")
}

metadata {
	definition (name: "Sinope technologie Ligthswitch", namespace: "Sinope Technologie", author: "Mathieu Virole") {
		capability "Switch"
		capability "Refresh"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.Lighting.light11", backgroundColor:"#79b821", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.Lighting.light13", backgroundColor:"#ffffff", nextState:"turningOn"
			}
		}
		
		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		standardTile("error", "device.error", width: 6, height: 2) {
		    state "default", label:'${currentValue}', backgroundColor:"#ffffff", icon:"st.Office.office8"
		}
		
		main "switch"
		details(["switch","refresh", "error"])
	}
}

def on() {

	if(!isLoggedIn()) {
		log.info "Need to login"
		login()
	}
	if(data.error==true){
		logout()
	}else{
    	def params = [
			uri: "${data.server}",
			path: "api/device/${data.deviceId}/intensity",
			headers: ['Session-Id' : data.auth.session],
		 	body: ['intensity': 100]
		]
	    requestApi("setDevice", params);
	    refresh();
	}    
}

def off() {

	if(!isLoggedIn()) {
		log.info "Need to login"
		login()
	}
	if(data.error==true){
		logout()
	}else{
		def params = [
			uri: "${data.server}",
			path: "api/device/${data.deviceId}/intensity",
			headers: ['Session-Id' : data.auth.session],
		 	body: ['intensity': 0]
		]
		requestApi("setDevice", params);
	    refresh();
	}    
}

def refresh() {

	if(!isLoggedIn()) {
		login()
	}
	if(data.error==true){
		logout()
	}else{
		DeviceData()
		runIn(15, refresh)
	}
}

def login() {
	data.server="https://dev.neviweb.com/"
	data.requested=false
    def params = [
        uri: "${data.server}",
        path: 'api/login',
        requestContentType: "application/x-www-form-urlencoded; charset=UTF-8",
        body: ["email": settings.email, "password": settings.password, "stayConnected": "0"]
    ]
    requestApi("login", params);
    if (data.auth.error){
    	log.warn(data.auth.error.code);
    	data.error = error(data.auth.error.code)
    	sendEvent(name: 'error', value: "${data.error}")
    	log.error("${data.error}")
    	data.error=true
    	logout()
	}else{
		log.info("login and password :: OK")
    	data.error=false
    	sendEvent(name: 'error', value: "")
    	gatewayId()
	} 
    
}

def logout() {
		data.gatewayId=null;
		data.deviceId=null;
      	def params = [
			uri: "${data.server}",
	        path: "api/logout",
	       	requestContentType: "application/x-www-form-urlencoded; charset=UTF-8",
	        headers: ['Session-Id' : data.auth.session]
    	]
        requestApi("logout", params);
        log.info("logout :: OK")  
}

def gatewayId(){
	def params = [
		uri: "${data.server}",
        path: "api/gateway",
       	requestContentType: "application/json, text/javascript, */*; q=0.01",
        headers: ['Session-Id' : data.auth.session]
    ]

    requestApi("gatewayList", params);
    
    def gatewayName=settings.gatewayname
    if (gatewayName!=null){
    	gatewayName=gatewayName.toLowerCase().replaceAll("\\s", "")
    }
	for(var in data.gateway_list){

    	def name_gateway=var.name
    	name_gateway=name_gateway.toLowerCase().replaceAll("\\s", "")

    	if(name_gateway==gatewayName){
    		data.gatewayId=var.id
    		log.info("gateway ID is :: ${data.gatewayId}")
    		sendEvent(name: 'error', value: "")
    		data.error=false
    		deviceId()
    	}else{
    		data.code=3001
    	}
    }
    if (data?.gatewayId==null){
    	data.error=error(data.code)
    	sendEvent(name: 'error', value: "${data.error}")
    	log.error("${data.error}")
    	data.error=true
    	logout()
    }
}

def deviceId(){

	def params = [
		uri: "${data.server}",
        path: "api/device",
        query: ['gatewayId' : data.gatewayId],
       	requestContentType: "application/json, text/javascript, */*; q=0.01",
        headers: ['Session-Id' : data.auth.session]
   	]
    
   	requestApi("deviceList", params);

    def deviceName=settings.devicename
    if (deviceName!=null){
    	deviceName=deviceName.toLowerCase().replaceAll("\\s", "")
    }
    for(var in data.devices_list){
    	def name_device=var.name
    	name_device=name_device.toLowerCase().replaceAll("\\s", "")
    	if(name_device==deviceName){
    		if(var.type==102 || var.type==112 || var.type==120){
    			data.deviceId=var.id
	    		log.info("device ID is :: ${data.deviceId}")
	    		sendEvent(name: 'error', value: "")
	    		DeviceData()
	    		data.error=false
	    		}else{
	    			data.code=4002
	    		}
    	}else{
    		data.code=4001
    	}	
    }
    if (data?.deviceId==null){
    	data.error=error(data.code)
    	sendEvent(name: 'error', value: "${data.error}")
    	log.error("${data.error}")
    	data.error=true
    	logout()
    }	
}

def DeviceData(){

   	def params = [
		uri: "${data.server}api/device/${data.deviceId}/data?force=1",
		requestContentType: "application/x-www-form-urlencoded; charset=UTF-8",
        headers: ['Session-Id' : data.auth.session]
    ]
    
    requestApi("deviceData", params);

    if (data.status.errorCode == null){
	    if (data.status.intensity==0){
	    	log.warn("lightswitch :: off")
	    	sendEvent(name: "switch", value: "off")
	    }else{
	    	log.warn("lightswitch :: on")
	    	sendEvent(name: "switch", value: "on")
	    }
    }else{
    	data.error=error(data.status.errorCode)
    	sendEvent(name: 'error', value: "${data.error}")
    	log.error("${data.error}")
    }
}

def isLoggedIn() {
	log.info ("Is it login?")
	if (data?.auth?.session!=null){
		try{
			def params = [
				uri: "${data.server}",
			    path: "api/gateway",
			   	requestContentType: "application/json, text/javascript, */*; q=0.01",
			    headers: ['Session-Id' : data.auth.session]
			]
			
			requestApi("sessionExpired", params);

			if(!data.auth) {
				return false
				log.error(error(1002))
			} else {
				if (data?.deviceId!=null){
					return true
				}else{
					return false
					log.error(error(1));
				}
			}
		}catch (e){
			log.error(e)
			return false
		}
	}else{
		return false
	}
}

def requestApi(actionApi, params){
	if (!data.requested){
		data.requested=true
		switch(actionApi){
			case "login":
				httpPost(params) { resp ->
			        data.auth = resp.data
			        data.requested=false
			    }
			break;
			case "logout":
				httpGet(params) {resp ->
					data.auth = resp.data
					data.requested=false
		        }
		    break;
		    case "gatewayList":

		    	httpGet(params) { response -> 
			        data.gateway_list = response.data
			        data.requested=false
			    }
			break;
			case "deviceList":
				httpGet(params) {resp ->
					data.devices_list = resp.data
					data.requested=false
			    }
			break;
			case "deviceData":
				httpGet(params) {resp ->
					data.status = resp.data
					data.requested=false
			    }
			break;
			case "setDevice":
				httpPut(params){
			    	resp ->resp.data
			    	data.requested=false
			      	log.info("Click -> API response :: ${resp.data}") 
			    }
			break;
			case "sessionExpired":
				httpGet(params) {resp ->
				    if(resp.data.sessionExpired==true){
				    	log.warn error(100)
				    	data.auth=""
				    }
				    data.requested=false
				}
			break; 
		}    
	}
}

def error(error){
	switch (error) {
		case 1: return "Gateway name or Device name is wrong."
		case 100: return "Your session expired."
        case 1005: return "This action cannot be executed while in demonstration mode."
        case 1004: return "The resource you are trying to access could not be found."
        case 1003: return "You are not authorized to see this resource."
        case 1002: return "Wrong e-mail address or password. Please try again."
        case 1101: return "The e-mail you have entered is already used.  Please select another e-mail address."
        case 1102: return "The password you have provided is incorrect."
        case 1103: return "The password you have provided is not secure."
        case 1104: return "The account you are trying to log into is not activated. Please activate your account by clicking on the activation link located in the activation email you have received after registring. You can resend the activation email by pressing the following button."
        case 1105: return "Your account is disabled."
        case 1110: return "The maximum login retry has been reached. Your account has been locked. Please try again later."
        case 1111: return "Your account is presently locked. Please try again later."
        case 1120: return "The maximum simultaneous connections on the same IP address has been reached. Please try again later."
        case 2001: return "The device you are trying to access is temporarily unaccessible. Please try later."
        case 2002: return "The network you are trying to access is temporarily unavailable. Please try later."
        case 2003: return "The web interface (GT125) that you are trying to add is already present in your account."
        case 3001: return "Wrong gateway name. Please try again."
        case 4001: return "Wrong device name. Please try again."
        case 4002: return "This device is not Ligthswitch. Please change DeviceName."
        default: return "An error has occurred, please try again later."

    }
}