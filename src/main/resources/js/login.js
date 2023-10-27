async function loginButton() {
    let inputUserID = document.getElementById("input_userid")
    let inputPassword = document.getElementById("input_password")
    let snuggles = document.getElementById("snuggles")
    let errorImg = document.getElementById("errorimg")
    let errorText = document.getElementById("errortext")
    errorImg.style.display = "none"
    errorText.style.display = "none"
    let resp = await fetch(`${root_data}/login`, {
        method: "POST", body: JSON.stringify({
            id: inputUserID.value, password: inputPassword.value
        }), headers: {
            "Content-Type": "application/json"
        }
    })
    let json = await resp.json()
    if (resp.ok) {
        snuggles.innerText = json.token
        localStorage.setItem("token", json.token)
        window.location.href = root_data + "/web"
    } else {
        errorImg.src = `https://http.cat/${json.error}`
        errorText.innerText = json.message
        errorImg.style.display = "unset"
        errorImg.style.maxWidth = "256px"
        errorImg.style.maxHeight = "256px"
        errorText.style.display = "unset"
    }
}
