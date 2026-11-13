---
name: qa-web
description: >
  QA tests for the PetClinic web UI: owner search and paging, the owner, pet and visit forms and
  their validation, the vets list, the error page and the language switch. Drives a real browser
  through droid-control against the branch running on this machine.
---

# QA: PetClinic web UI

## Testing Target

PetClinic has no preview deployments and no hosted environments. Test the branch's own code on a local server.

If the QA orchestrator passed the base URL of a server it started from this branch, use it and skip to "Authentication". Otherwise start one from the repository root. Shell variables do not carry over between commands, because each command can run in a new shell, so write the literal values into later commands: below, `<RUN_ID>`, `<PORT>`, `<PID>` and `<BASE_URL>` stand for them.

1. Create the evidence directory: `RUN_ID="$(date +%s)"; mkdir -p "qa-results/$RUN_ID" && echo "$RUN_ID"`.
2. Build: `./mvnw -B -q -DskipTests -Dspring-javaformat.skip=true -Dcheckstyle.skip=true package`. It skips the format and nohttp checks, which CI runs separately.
3. Pick a free port: 8080, or another such as 18080. A port is free when `curl -s -o /dev/null http://localhost:<PORT>/` cannot connect.
4. Start the server as a background job that keeps running after the command returns, and note its process ID. In Droid, run this through the Execute tool with `fireAndForget: true`, which returns the process ID; a job started with a plain `&` is stopped when its command ends.
   ```bash
   exec java -jar target/spring-petclinic-*.jar --server.port=<PORT> > qa-results/<RUN_ID>/app.log 2>&1
   ```
5. Poll `curl -fsS http://localhost:<PORT>/actuator/health` every 2 seconds, for up to 120 seconds, until it contains `"status":"UP"`. If the process exits meanwhile (`kill -0 <PID>` fails), the start failed: read `app.log`.
6. Check that `git.commit.id` in `http://localhost:<PORT>/actuator/info` is the start of `git rev-parse HEAD`, so you know the server runs this checkout.
7. `<BASE_URL>` is `http://localhost:<PORT>`.

NEVER fall back to any other URL: no other server runs this branch's code. If the server does not start, report every web flow as BLOCKED and include the last 50 lines of `app.log`. Stop the server you started when you finish: `kill <PID>`.

## Authentication

None. PetClinic has no login, accounts or roles, so there are no secrets or environment variables to set, in CI or locally. Every flow runs as the `visitor` persona.

## How to drive the browser

Invoke the `droid-control` skill before interacting with the app. Use its web page route (browser-use, which drives `agent-browser`) plus the Capture and Verify stages. Load Compose only when both `video_evidence` and `droid_control.compose` are true in `.factory/skills/qa/config.yaml`.

This file says WHAT to test and what counts as success. droid-control decides HOW to open pages, click, type, wait and capture. Take a fresh snapshot after every navigation or form submission before acting again.

## App notes

- Pages are rendered on the server (Spring MVC and Thymeleaf). Every form submission loads a new page. There is no validation in the browser: the server re-renders the form with a message under each invalid field.
- A successful save redirects to the owner's page, which shows a green message at the top, such as "New Owner Created". A script hides that message 3 seconds after the page loads (`src/main/resources/templates/owners/ownerDetails.html`). Check for it right after the page loads, as part of the submit step (wait for the text), or read the text of the `#success-message` element, which stays in the page while hidden. The saved values on the owner's page are lasting proof of the save.
- Navigation bar: Home (`/`), Find Owners (`/owners/find`), Veterinarians (`/vets.html`) and Error (`/oups`).
- Seed data, reloaded on every start because H2 runs in memory:
  - 10 owners, 5 per page (2 pages). Betty Davis and Harold Davis share a last name. George Franklin is owner 1 and has the cat Leo (pet 1).
  - 6 vets, 5 per page: James Carter to Henry Stevens on page 1, Sharon Jenkins on page 2. Vets without a specialty show "none".
  - Pet types, in the order the form lists them: bird, cat, dog, hamster, lizard, snake.
- New owners get the next free id (11 on a fresh server). Read the id from the address after saving instead of assuming it.
- Records you create stay until the server stops. Include the run ID in every name you create (for example last name `Qa<RUN_ID>`; names allow 30 characters), so flows and repeated runs do not collide.
- Date fields are native `<input type="date">` controls. The visit form's date defaults to tomorrow and does not allow earlier dates; the pet birth date starts empty.
- The language is chosen with the `lang` URL parameter (for example `?lang=de`) and kept for the rest of the browser session.

## Flow menu

The orchestrator picks the flows relevant to the diff. "Covers" lists the code each flow exercises. A change to `pom.xml` covers every flow.

Run the flows that only read data (W1, W2, W3, W7, W8, W9) before the flows that create records (W4, W5, W6, and ad-hoc tests that save data). W2 and W3 expect the 10 seed owners, and every owner you add changes the page counts. If this server already has more owners, work out the expected pages from the current number of owners (5 per page).

### W1. Home page and navigation

Covers: `system/WelcomeController.java`, `templates/welcome.html`, `templates/fragments/layout.html`, `static/**`, `scss/**`.

1. Open `/`. **Pass:** the "Welcome" heading and the pets picture are shown, and the page is styled (a dark navigation bar with the four links, not unstyled HTML).
2. Follow Home, Find Owners and Veterinarians from the navigation bar. **Pass:** each page loads with its heading ("Welcome", "Find Owners", "Veterinarians") and the bar marks the current page.

### W2. Find owners

Covers: `owner/OwnerController.java` (`processFindForm`), `owner/OwnerRepository.java`, `templates/owners/findOwners.html`, `templates/owners/ownersList.html`.

1. On Find Owners, search for last name `Davis`. **Pass:** a list with exactly Betty Davis and Harold Davis.
2. Search for `davis` in lower case. **Pass:** the same two owners (the search ignores case).
3. Search for `Franklin`. **Pass:** the browser goes straight to George Franklin's page (`/owners/1`), with no list in between.
4. Negative: search for `Zzqa<RUN_ID>`. **Pass:** the find form again, with "has not been found" under the last name field.
5. Search with the field empty. **Pass:** the list of all owners, page 1 of 2 with the seed data.

### W3. Owner list paging and page-number bounds

Covers: `OwnerController.processFindForm` and `findPaginatedForOwnersLastName`, `templates/owners/ownersList.html`, `templates/fragments/layout.html`.

1. Open `/owners?page=1`, then use the pager to go to page 2. **Pass:** 5 owners on each page; the pager shows the current page and links for the others.
2. Negative: open the page after the last one (`/owners?page=3` with the seed data), `/owners?page=0` and `/owners?page=-1`. **Pass:** each lands on page 1 of the list (address `/owners?page=1`), with no error page.
3. Negative: open `/owners?page=999999999` and `/owners?page=2147483647`. **Pass:** each lands on page 1 of the list with no error page. Before the overflow fix these returned HTTP 500.
4. Negative: open `/owners?lastName=Davis&page=9`. **Pass:** page 1 of the Davis results (address `/owners?page=1&lastName=Davis`); the filter is kept.
5. Negative: open `/owners?page=abc`. **Pass:** the PetClinic error page with "An unexpected error occurred." (HTTP 400), not a 500.

### W4. Add and edit an owner

Covers: `OwnerController` (`/owners/new`, `/owners/{id}/edit`), `owner/Owner.java`, `model/Person.java`, `templates/owners/createOrUpdateOwnerForm.html`, `templates/owners/ownerDetails.html`, `messages/**`.

1. Find Owners, then Add Owner. Enter first name `Qa`, last name `Qa<RUN_ID>`, address `1 Main St`, city `Madison`, telephone `6085550000`, and submit. **Pass:** the owner's page with "New Owner Created" (see App notes: it hides after 3 seconds) and the values you entered.
2. Edit Owner on that page, change the city to `Monona`, submit. **Pass:** the owner's page with "Owner Values Updated" and the new city.
3. Negative: add an owner with telephone `12345`. **Pass:** the form again with "Telephone must be a 10-digit number"; nothing is saved.
4. Negative: submit the add form with every field empty. **Pass:** "must not be blank" under each field.
5. Negative: enter a city of 81 characters (or an address of 256, or a first name of 31). **Pass:** "size must be between 0 and 80" (255 or 30 for the other fields) under the field, and no error page.

### W5. Add and edit a pet

Covers: `owner/PetController.java`, `owner/PetValidator.java`, `owner/PetTypeFormatter.java`, `owner/Pet.java`, `templates/pets/createOrUpdatePetForm.html`.

Use the owner created in W4, or create one first.

1. Add New Pet: name `Rex<RUN_ID>` (30 characters at most), a past birth date such as `2020-05-01`, type `dog`. **Pass:** the owner's page with "New Pet has been Added" and the pet under Pets and Visits, showing the birth date and type.
2. Edit Pet: change the name, submit. **Pass:** "Pet details has been edited" and the new name.
3. Negative: add a pet with the same name as an existing pet of this owner. **Pass:** "is already in use" under the name.
4. Negative: birth date in the future (for example `2999-01-01`). **Pass:** "invalid date" under the birth date.
5. Negative: a pet without a type. The type list has no empty choice, so a browser always sends one; send the form request with curl instead: `curl -sS -w '\nHTTP %{http_code}\n' -d "name=NoType<RUN_ID>&birthDate=2020-01-01" <BASE_URL>/owners/<owner id>/pets/new`. **Pass:** HTTP 200, and the re-rendered form in the body contains "is required".
6. Negative: a name of 31 characters. **Pass:** "Name must be no more than 30 characters".

### W6. Book a visit

Covers: `owner/VisitController.java`, `owner/Visit.java`, `templates/pets/createOrUpdateVisitForm.html`, `templates/owners/ownerDetails.html`.

1. On an owner's page, choose Add Visit for a pet. Keep the default date (tomorrow), enter the description `QA check-up <RUN_ID>`, submit. **Pass:** the owner's page with "Your visit has been booked", and the visit date and description listed under the pet.
2. Negative: a blank description. **Pass:** "must not be blank".
3. Negative: a description of 256 characters. **Pass:** "size must be between 0 and 255", and no error page.
4. Negative: today's date. **Pass:** either the browser refuses to submit (the field's minimum is tomorrow, and Chrome blocks earlier dates before sending the form) or the server re-renders the form with "Visit date must be in the future". Record which of the two happened.

### W7. Vets list and paging

Covers: `vet/VetController.java`, `vet/Vet.java`, `vet/Specialty.java`, `templates/vets/vetList.html`.

1. Veterinarians: **Pass:** 5 vets with their specialties ("none" where there are none) and a pager. Page 2 shows Sharon Jenkins.
2. Negative: open `/vets.html?page=3`, `/vets.html?page=0` and `/vets.html?page=999999999`. **Pass:** each lands on `/vets.html?page=1` with no error page.

### W8. Error page

Covers: `system/CrashController.java`, `templates/error.html`, error handling settings in `application.properties`.

1. Follow Error in the navigation bar (`/oups`). **Pass:** the PetClinic error page, inside the normal layout with the navigation bar: "Something happened..." and "An internal server error occurred.". HTTP 500 is expected; this page exists to demonstrate error handling.
2. Negative: open `/does-not-exist`. **Pass:** the same error page with "The requested page was not found." (HTTP 404).

### W9. Language switch

Covers: `system/WebConfiguration.java`, `messages/**`, templates.

1. Open `/?lang=de`. **Pass:** "Willkommen", and the navigation bar shows "Startseite" and "Besitzer suchen".
2. Follow a navigation link without the parameter. **Pass:** the page is still in German (the choice is kept in the session).
3. Open `/?lang=en`. **Pass:** English again ("Welcome").
4. Negative: `/?lang=xx`. **Pass:** the English page, no error.

## Known Failure Modes

1. **Unknown owner or pet id returns HTTP 500.** `/owners/9999` and `/owners/1/pets/9999/edit` show the error page with "An internal server error occurred." This is known issue F3 in `docs/RUNBOOK.md` (upstream fix: spring-projects/spring-petclinic#2688). Report it as FAIL only when the diff changes owner or pet lookup; otherwise mention it as known.
2. **`/oups` always fails.** It throws on purpose. PASS when the PetClinic error page renders; the exception text is not shown on the page.
3. **Names collide within a run.** Records stay until the server stops, so a reused pet name for the same owner fails with "is already in use". Include the run ID in names.
4. **Address bar shows `;jsessionid=...`.** The first redirect of a new browser session can carry the session id in the URL. Ignore it when comparing addresses.
5. **Date inputs.** Filling a date field can leave it empty: with agent-browser 0.43 in an en-US Chrome, `fill` and `type` do not change an `<input type="date">`. Focus the field (for example `#birthDate`), press each digit of the date in MMDDYYYY order, and read the value back before submitting.
6. **Port 8080 already in use.** Another server, possibly running different code, holds the port. Start this branch on another free port; never test the existing server.
7. **Success message missing from a snapshot.** The green message on the owner's page hides itself 3 seconds after the page loads, so a later snapshot no longer shows it. That is not a failure: check it right after the page loads or read `#success-message`, and use the saved values as proof.
8. **Server gone before the first flow.** A server started with a plain `&` is stopped when its command returns. Start it as described in Testing Target.
9. **Navigation race.** An `open`, `click` or snapshot sent right after a click that loads a page can fail with `net::ERR_ABORTED` or "Element not found", or capture the old page. After every navigation or form submit, wait for the page load to finish (with agent-browser: `wait --load networkidle`) before the next command.
