// OAuth login using HelloJS

function login(site) {
  hello(site).login({ 'scope' : 'email' }).then(
    function() {
      var session = hello(site).getAuthResponse();
      var xhr = new XMLHttpRequest();

      xhr.open('POST', '/');
      xhr.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');

      xhr.onload = function() {
        if (this.responseText == 'success') {

          // Let's give more of a visual representation of being logged in
          if (document.getElementById(site + '-login-response') === null) {
            hello(site).api('me').then(function(json) {

              var button = document.getElementById(site + 'Button');
              button.innerHTML = 'Logged into ' + site.charAt(0).toUpperCase() + site.slice(1);

              // replace generic banner with personalized greeting
              constructGreetingBanner(json.name);

              // store username that is used to construct personalized banner
              localStorage.setItem('sinai-scholars-username', json.name);  
            });
          }

          console.log('Signed into ' + site);
        } else {

          // Something went wrong
          try {

            // See if the response is JSON
            var serverResponse = JSON.parse(this.responseText);
            if (serverResponse.message === 'Not an allowed email') {
              alert('Please log out of the Google account "' + serverResponse.email + '" to proceed.');
            }
          } catch (e) {
            if (e instanceof SyntaxError) {
              // this.responseText is not JSON
            }
            else {
              // some other error
              throw e;
            }
          }
          console.log('Server response: ' + this.responseText);
        }
      };

      xhr.send('token=' + session.access_token + '&site=' + site);
    }, function(e) {
      console.log('Login error: ' + e.error.message);
    }
  );

  // close the login dialog box
  setTimeout(function() { $('#hide-login').click(); }, 1000);
}

// Generates and inserts personalized message into the site banner

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


// Handles the display of the login dialog box

$(document).ready(function() {

    // use to force login dialog box popup after redirect to "/"
    localStorageKey = 'sinai-user-wants-to-log-in';

    // if key-value pair exists, then show the dialog box
    if (localStorage.getItem(localStorageKey) !== null)
    {
        $("#login-form").css("visibility", "visible");
    }
    
    $("button#hide-login").on("click", function() {
        $("#login-form").css("visibility", "hidden");
        localStorage.removeItem(localStorageKey);
    });

    $("a").on("click", function() {

        // if the login link is clicked, set localStorage
        // otherwise, clear that key-value pair if it exists
        if ($(this).attr("id") == "show-login")
        {
            localStorage.setItem(localStorageKey, true);
        }
        else
        {
            localStorage.removeItem(localStorageKey);
        }
    });
});
