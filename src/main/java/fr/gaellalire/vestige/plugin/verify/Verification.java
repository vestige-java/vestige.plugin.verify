/*
 * This file is part of Vestige.
 *
 * Vestige is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Vestige is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Vestige.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.gaellalire.vestige.plugin.verify;

import java.io.File;

/**
 * @author Gael Lalire
 */
public class Verification {

    private File verifyFile;

    private File verificationMetadataFile;

    private String mavenPropertyName;

    public File getVerifyFile() {
        return verifyFile;
    }

    public void setVerifyFile(final File verifyFile) {
        this.verifyFile = verifyFile;
    }

    public File getVerificationMetadataFile() {
        return verificationMetadataFile;
    }

    public void setVerificationMetadataFile(final File verificationMetadataFile) {
        this.verificationMetadataFile = verificationMetadataFile;
    }

    public String getMavenPropertyName() {
        return mavenPropertyName;
    }

    public void setMavenPropertyName(final String mavenPropertyName) {
        this.mavenPropertyName = mavenPropertyName;
    }
}
