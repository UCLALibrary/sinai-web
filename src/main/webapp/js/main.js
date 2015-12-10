/*
 * constructGreetingBanner
 *
 * Generates and inserts personalized message into the site banner
 */
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

/*
 * This function adds onclick event listeners for:
 *
 * 1. login link in banner (brings up login dialog box)
 * 2. cancel button inside that dialog box (closes dialog box)
 */
$(document).ready(function() {

	$("a#show-login").on("click", function() {
		$("#login-form").css("visibility", "visible");
	});
	
	$("button#hide-login").on("click", function() {
		$("#login-form").css("visibility", "hidden");
	});

});
