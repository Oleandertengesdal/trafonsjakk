package no.trafonsjakk.analysis;

import no.trafonsjakk.analysis.AnalysisService.AnalysisDto;
import no.trafonsjakk.analysis.AnalysisService.CalibrationDto;
import no.trafonsjakk.analysis.AnalysisService.PlayerAccuracyDto;
import no.trafonsjakk.analysis.AnalysisService.SaveRequest;
import no.trafonsjakk.common.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Saved engine analyses, accuracy on profiles and the "played like" calibration. */
@RestController
public class AnalysisController {

    private final AnalysisService analysis;

    public AnalysisController(AnalysisService analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/api/games/{id}/analysis")
    AnalysisDto get(@PathVariable long id) {
        return analysis.get(id);
    }

    @PostMapping("/api/games/{id}/analysis")
    AnalysisDto save(@PathVariable long id, @RequestBody SaveRequest req, @AuthenticationPrincipal Jwt jwt) {
        return analysis.save(id, req, CurrentUser.of(jwt));
    }

    @DeleteMapping("/api/admin/games/{id}/analysis")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        analysis.delete(id);
    }

    @GetMapping("/api/users/{username}/accuracy")
    PlayerAccuracyDto forPlayer(@PathVariable String username) {
        return analysis.forPlayer(username);
    }

    @GetMapping("/api/analysis/calibration")
    CalibrationDto calibration() {
        return analysis.calibration();
    }
}
