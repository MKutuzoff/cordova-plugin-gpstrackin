var gpstracking = {
	start:function(successCallback, errorCallback) {
		var win = function() {
            successCallback();
		}
		var fail = function() {
			errorCallback();
		}
		exec(win, fail, "GpsTracking", "start");
	}
}