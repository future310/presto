/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.localfile;

import com.facebook.presto.spi.PrestoException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.facebook.presto.localfile.LocalFileErrorCode.LOCAL_FILE_ERROR_CODE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

final class DataLocation
{
    private final File location;
    private final Optional<String> pattern;
    private final Optional<Pattern> compiledPattern;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @JsonCreator
    public DataLocation(
            @JsonProperty("location") String location,
            @JsonProperty("pattern") Optional<String> pattern)
    {
        requireNonNull(location, "location is null");
        requireNonNull(pattern, "pattern is null");

        File file = new File(location);
        if (!file.exists() && pattern.isPresent()) {
            file.mkdirs();
        }

        checkArgument(file.exists(), "location does not exist");
        if (pattern.isPresent() && !file.isDirectory()) {
            throw new IllegalArgumentException("pattern may be specified only if location is a directory");
        }

        this.location = file;
        this.pattern = (!pattern.isPresent() && file.isDirectory()) ? Optional.of(".*") : pattern;
        this.compiledPattern = file.isDirectory() ? Optional.of(Pattern.compile(pattern.orElse(".*"))) : Optional.empty();
    }

    @JsonProperty
    public File getLocation()
    {
        return location;
    }

    @JsonProperty
    public Optional<String> getPattern()
    {
        return pattern;
    }

    public List<File> files()
    {
        checkState(location.exists(), "location %s doesn't exist", location);
        if (!pattern.isPresent()) {
            return ImmutableList.of(location);
        }

        checkState(location.isDirectory(), "location %s is not a directory", location);
        File[] files = location.listFiles(file -> compiledPattern.get().matcher(file.getName()).matches());

        if (files == null) {
            throw new PrestoException(LOCAL_FILE_ERROR_CODE, format("Failed to list files at %s", location));
        }

        Arrays.sort(files, (o1, o2) -> Long.compare(o2.lastModified(), o1.lastModified()));
        return ImmutableList.copyOf(files);
    }
}
