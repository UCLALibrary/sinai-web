$("#about").mouseenter(function() {
	 $("#sub-navbar").css("visibility", "visible");
	 
}).mouseleave(function() {
	var sub_navbar = $("#sub-navbar");

	if (sub_navbar.is(":hover") || $("#main-navbar").is(":hover")) {
		return;
	}
	console.log("hide1");
	sub_navbar.css("visibility", "hidden");
});

$("#sub-navbar").mouseleave(function() {
	if ($("#about").is(":hover")) {
		return;
	}
	console.log("hide2");
	$(this).css("visibility", "hidden");
});

$("#main-navbar").mouseleave(function() {
	if ($("#sub-navbar").is(":hover")) {
		return;
	}
	console.log("hide3");
	$("#sub-navbar").css("visibility", "hidden");
});

$("a#show-login").on("click", function() {
	$("#login-form").css("visibility", "visible");
});

$("button#hide-login").on("click", function() {
	$("#login-form").css("visibility", "hidden");
});
