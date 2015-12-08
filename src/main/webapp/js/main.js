function constructGreetingBanner(username) {

    var bannerText = document.getElementById('greeting');
    var logoutLink = document.createElement('a');
    var nameStrongText = document.createElement('b');
    var linkStrongText = document.createElement('b');

    nameStrongText.innerHTML = username;
    linkStrongText.innerHTML = 'logout';
    logoutLink.setAttribute('href', '/logout');
	logoutLink.setAttribute('onclick', 'logout()');
    logoutLink.appendChild(linkStrongText);

    bannerText.innerHTML = 'welcome, ';
    bannerText.appendChild(nameStrongText);
    bannerText.appendChild(document.createTextNode(' | '));
    bannerText.appendChild(logoutLink);

}

$(document).ready(function() {

	$("a#show-login").on("click", function() {
		$("#login-form").css("visibility", "visible");
	});
	
	$("button#hide-login").on("click", function() {
		$("#login-form").css("visibility", "hidden");
	});

});
